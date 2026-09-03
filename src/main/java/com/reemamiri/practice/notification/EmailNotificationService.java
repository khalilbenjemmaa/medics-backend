package com.reemamiri.practice.notification;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Sends appointment email.
 *
 * Each message is multipart: an HTML part styled like the practice, and
 * a plain-text alternative. The text part is not a courtesy — some
 * clients render it by choice, and a message with no text alternative
 * scores worse with spam filters.
 *
 * The confirmation carries an .ics attachment, which is the email's
 * equivalent of the "Add to calendar" button on the confirmation
 * screen.
 *
 * Active only when `app.notifications.email.enabled` is true. The
 * default remains the logging implementation, because a half-configured
 * mail client that silently drops messages is worse than none: the
 * practice would believe patients had been told.
 *
 * @Async so sending never sits inside a booking request, and failures
 * are logged rather than thrown for the same reason the meeting
 * provider's are — the appointment is real whether or not the email
 * arrived, and failing a booking because an SMTP server was down would
 * be untrue.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.notifications.email", name = "enabled", havingValue = "true")
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AppProperties properties;
    private final PracticeDetails practice;

    /** Practice identity as it appears in email. */
    public record PracticeDetails(
            String name, String practitionerName, String practitionerRole,
            String address, String phone, String phoneHref, String replyTo) {}

    public EmailNotificationService(JavaMailSender mailSender, TemplateEngine templateEngine,
                                    AppProperties properties, PracticeDetails practice) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.properties = properties;
        this.practice = practice;
    }

    @Override
    @Async
    public void appointmentConfirmed(Appointment appointment) {
        send(appointment, "appointment-confirmed",
                "Your appointment is confirmed — " + appointment.getReference(), true);
    }

    @Override
    @Async
    public void appointmentCancelled(Appointment appointment) {
        send(appointment, "appointment-cancelled",
                "Your appointment has been cancelled — " + appointment.getReference(), false);
    }

    @Override
    @Async
    public void appointmentRescheduled(Appointment appointment) {
        send(appointment, "appointment-rescheduled",
                "Your appointment has moved — " + appointment.getReference(), true);
    }

    private void send(Appointment appointment, String template, String subject, boolean withInvite) {
        try {
            Context context = context(appointment, subject);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());

            helper.setFrom(properties.notifications().email().from());
            helper.setTo(appointment.getPatient().getEmail());
            helper.setSubject(subject);
            if (practice.replyTo() != null && !practice.replyTo().isBlank()) {
                // So "reply to this email" in the body actually reaches
                // someone, rather than a noreply address.
                helper.setReplyTo(practice.replyTo());
            }

            // Text first, then HTML: MimeMessageHelper orders the
            // alternative parts so the richest is last, which is what
            // clients pick from.
            helper.setText(
                    templateEngine.process("email/" + template + ".txt", context),
                    templateEngine.process("email/" + template + ".html", context));

            if (withInvite) {
                String ics = CalendarInvite.build(appointment, practice.practitionerName(),
                        properties.notifications().email().from(), location(appointment));
                helper.addAttachment("appointment.ics",
                        new ByteArrayResource(ics.getBytes(StandardCharsets.UTF_8)),
                        "text/calendar; charset=UTF-8; method=PUBLISH");
            }

            /*
             * Headers marking this as transactional rather than bulk.
             *
             * These help at the margin; they do not fix a reputation
             * problem. A filter's main question is whether this sender
             * has a history of wanted mail, which no header answers.
             */
            // RFC 3834: generated by a system, not typed by a person.
            message.setHeader("Auto-Submitted", "auto-generated");
            // Suppresses out-of-office replies bouncing back at us.
            message.setHeader("X-Auto-Response-Suppress", "OOF, AutoReply");
            // Signals a service message, not a campaign.
            message.setHeader("X-Entity-Ref-ID", appointment.getReference());

            mailSender.send(message);
            // The reference identifies it. The patient's address is not
            // written to the log.
            log.info("Sent '{}' for appointment {}", template, appointment.getReference());

        } catch (Exception ex) {
            log.error("Could not email about appointment {}", appointment.getReference(), ex);
        }
    }

    private Context context(Appointment appointment, String subject) {
        ZoneId zone = properties.doctorTimezone();
        var local = appointment.getStartAt().atZone(zone);

        Context context = new Context(Locale.UK);
        context.setVariable("subject", subject);
        context.setVariable("practiceName", practice.name());
        context.setVariable("practitionerName", practice.practitionerName());
        context.setVariable("practitionerRole", practice.practitionerRole());
        context.setVariable("address", practice.address());
        context.setVariable("phone", practice.phone());
        context.setVariable("phoneHref", practice.phoneHref());

        context.setVariable("firstName", appointment.getPatient().getFirstName());
        context.setVariable("reference", appointment.getReference());
        context.setVariable("concern", appointment.getConcernCategory().getName());
        context.setVariable("longDate",
                local.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.UK)));
        context.setVariable("time", local.format(DateTimeFormatter.ofPattern("HH:mm")));
        context.setVariable("timezone", zone.getId());
        context.setVariable("online", appointment.isOnline());
        context.setVariable("meetingUrl", appointment.getMeetingUrl());
        context.setVariable("location", location(appointment));
        return context;
    }

    private String location(Appointment appointment) {
        return appointment.isOnline() ? "Online consultation" : practice.address();
    }
}
