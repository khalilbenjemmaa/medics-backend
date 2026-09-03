package com.reemamiri.practice.notification;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends appointment email over SMTP.
 *
 * Active only when `app.notifications.email.enabled` is true AND a mail
 * sender is configured, so the default remains the logging
 * implementation. That ordering is deliberate: a half-configured mail
 * client that silently drops messages is worse than none, because the
 * practice would believe patients had been told.
 *
 * @Async because sending is slow and must never sit inside a booking
 * request. A failure is logged and swallowed for the same reason the
 * meeting provider's is — the appointment is real whether or not the
 * email arrived, and telling the patient their booking failed because
 * an SMTP server was down would be untrue.
 *
 * NOT VERIFIED END TO END: no SMTP credentials were available while
 * this was written. The wiring is conventional but treat the first real
 * send as a test.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.notifications.email", name = "enabled", havingValue = "true")
public class EmailNotificationService implements NotificationService {

    private final JavaMailSender mailSender;
    private final AppProperties properties;
    private final String from;

    public EmailNotificationService(JavaMailSender mailSender, AppProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
        this.from = properties.notifications().email().from();
    }

    @Override
    @Async
    public void appointmentConfirmed(Appointment appointment) {
        send(appointment, "Your appointment is confirmed",
                "Your appointment is confirmed for " + localTime(appointment) + "."
                        + (appointment.isOnline() && appointment.getMeetingUrl() != null
                                ? "\n\nJoin here: " + appointment.getMeetingUrl()
                                : "")
                        + "\n\nReference: " + appointment.getReference());
    }

    @Override
    @Async
    public void appointmentCancelled(Appointment appointment) {
        send(appointment, "Your appointment has been cancelled",
                "The appointment on " + localTime(appointment) + " has been cancelled."
                        + "\n\nReference: " + appointment.getReference());
    }

    @Override
    @Async
    public void appointmentRescheduled(Appointment appointment) {
        send(appointment, "Your appointment has moved",
                "Your appointment is now " + localTime(appointment) + "."
                        + "\n\nReference: " + appointment.getReference());
    }

    private void send(Appointment appointment, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(appointment.getPatient().getEmail());
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            // The reference identifies it; the address does not go in the log.
            log.info("Sent '{}' for appointment {}", subject, appointment.getReference());
        } catch (MailException ex) {
            log.error("Could not email about appointment {}", appointment.getReference(), ex);
        }
    }

    private String localTime(Appointment appointment) {
        return appointment.getStartAt()
                .atZone(properties.doctorTimezone())
                .format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM 'at' HH:mm"));
    }
}
