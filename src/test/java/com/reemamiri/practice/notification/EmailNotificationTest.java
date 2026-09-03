package com.reemamiri.practice.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.reemamiri.practice.AbstractIntegrationTest;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.dto.PatientRequest;
import com.reemamiri.practice.booking.service.BookingService;
import com.reemamiri.practice.config.AppProperties;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * The booking confirmation email, verified against a real SMTP server
 * running in-process.
 *
 * GreenMail rather than a mocked JavaMailSender: a mock proves the code
 * called send(), not that a well-formed message came out the other end.
 * Everything asserted here — the headers, both MIME parts, the
 * attachment — is read back off the wire.
 */
@TestPropertySource(properties = {
        "app.notifications.email.enabled=true",
        "app.notifications.email.from=noreply@practice.test",
        "app.notifications.email.reply-to=contact@practice.test",
        "spring.mail.host=127.0.0.1",
        "spring.mail.port=3025",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
})
class EmailNotificationTest extends AbstractIntegrationTest {

    @RegisterExtension
    static final GreenMailExtension SMTP =
            new GreenMailExtension(ServerSetupTest.SMTP).withPerMethodLifecycle(true);

    @Autowired private BookingService bookingService;
    @Autowired private ConcernCategoryRepository categoryRepository;
    @Autowired private AppProperties properties;

    @Test
    @DisplayName("booking sends a confirmation carrying the details from the confirmation screen")
    void confirmationEmailContainsTheBookingDetails() throws Exception {
        var booking = book(ConsultationType.ON_SITE, slot(10, 0), "ada@example.test", "Ada");

        MimeMessage message = waitForOneMessage();

        assertThat(message.getSubject())
                .isEqualTo("Your appointment is confirmed — " + booking.reference());
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("ada@example.test");
        assertThat(message.getFrom()[0].toString()).contains("noreply@practice.test");
        // The body invites a reply, so replies must reach a real inbox.
        assertThat(message.getReplyTo()[0].toString()).contains("contact@practice.test");

        String html = partContaining(message, "text/html");
        String text = partContaining(message, "text/plain");

        // Every field the confirmation screen shows.
        for (String expected : new String[] {
                booking.reference(), "Ada", "Sensory integration", "Reem Amiri", "10:00",
        }) {
            assertThat(html).as("HTML contains %s", expected).contains(expected);
            assertThat(text).as("text contains %s", expected).contains(expected);
        }
        assertThat(html).contains("Your appointment is confirmed");
        assertThat(html).contains(properties.practice().address());

        // A text alternative is not decoration: some clients render it
        // by choice, and its absence is a spam signal.
        assertThat(text).isNotBlank();
    }

    @Test
    @DisplayName("the confirmation carries a valid calendar invitation")
    void confirmationCarriesCalendarInvite() throws Exception {
        var booking = book(ConsultationType.ON_SITE, slot(11, 0), "grace@example.test", "Grace");

        MimeMessage message = waitForOneMessage();
        String ics = partContaining(message, "text/calendar");

        assertThat(ics)
                .contains("BEGIN:VCALENDAR")
                .contains("BEGIN:VEVENT")
                .contains("UID:" + booking.reference() + "@reemamiri")
                .contains("STATUS:CONFIRMED")
                .contains("END:VCALENDAR");

        // UTC stamps: every client resolves those to the reader's zone,
        // whereas local times would need a VTIMEZONE block.
        assertThat(ics).containsPattern("DTSTART:\\d{8}T\\d{6}Z");
        assertThat(ics).containsPattern("DTEND:\\d{8}T\\d{6}Z");
    }

    @Test
    @DisplayName("an online booking with no meeting link never claims one is coming")
    void onlineWithoutLinkMakesNoPromise() throws Exception {
        book(ConsultationType.ONLINE, slot(14, 0), "linus@example.test", "Linus");

        MimeMessage message = waitForOneMessage();
        String html = partContaining(message, "text/html");

        assertThat(html).contains("will contact you with");
        // The exact failure this guards against: a fabricated or
        // promised link that does not exist.
        assertThat(html).doesNotContain("Join the meeting");
        assertThat(html).doesNotContain("meet.google.com");
    }

    /* ---------------- helpers ---------------- */

    private MimeMessage waitForOneMessage() {
        // Sending is @Async, so the assertion has to wait for the
        // delivery rather than assume it already happened.
        await().atMost(Duration.ofSeconds(10))
                .until(() -> SMTP.getReceivedMessages().length >= 1);
        return SMTP.getReceivedMessages()[0];
    }

    /** The decoded content of the first part whose type matches. */
    private String partContaining(MimeMessage message, String mimeType) throws Exception {
        return findPart(message.getContent(), mimeType);
    }

    private String findPart(Object content, String mimeType) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                var part = multipart.getBodyPart(i);
                if (part.isMimeType(mimeType)) {
                    Object body = part.getContent();
                    return body instanceof String s ? s : new String(part.getInputStream().readAllBytes());
                }
                String nested = findPart(part.getContent(), mimeType);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private com.reemamiri.practice.booking.dto.BookingResponse book(
            ConsultationType type, Instant startAt, String email, String firstName) {
        UUID categoryId = categoryRepository
                .findByActiveTrueOrderByDisplayOrderAscNameAsc().get(0).getId();
        return bookingService.book(
                new CreateBookingRequest(categoryId, type, startAt,
                        new PatientRequest(firstName, "Tester", email, "+33600000000", null), null),
                UUID.randomUUID().toString());
    }

    private Instant slot(int hour, int minute) {
        LocalDate date = LocalDate.now(properties.doctorTimezone()).plusDays(2);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date.atTime(hour, minute).atZone(properties.doctorTimezone()).toInstant();
    }
}
