package com.reemamiri.practice.notification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import com.reemamiri.practice.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Writes the rendered emails to target/email-preview for eyeballing.
 *
 * Not an assertion — EmailNotificationTest covers correctness. This
 * exists because an email template is a visual artefact, and the only
 * way to know it looks right is to look at it.
 */
class EmailPreviewTest extends AbstractIntegrationTest {

    /*
     * The application's own engine, rather than a hand-built one. A
     * standalone TemplateEngine defaults to OGNL, which Spring Boot
     * does not ship — and more importantly, rendering with a different
     * engine than production uses would prove nothing about production.
     */
    @Autowired private TemplateEngine engine;

    @Test
    void writePreviews() throws Exception {
        Path out = Path.of("target/email-preview");
        Files.createDirectories(out);

        for (String name : new String[] {
                "appointment-confirmed", "appointment-cancelled", "appointment-rescheduled" }) {
            for (boolean online : new boolean[] { false, true }) {
                Context context = context(online);
                String html = engine.process("email/" + name + ".html", context);
                Files.writeString(out.resolve(name + (online ? "-online" : "-onsite") + ".html"), html);
            }
        }
    }

    private Context context(boolean online) {
        Context c = new Context(Locale.UK);
        c.setVariable("subject", "Your appointment is confirmed — OT-7K2M9P");
        c.setVariable("practiceName", "Reem Amiri");
        c.setVariable("practitionerName", "Reem Amiri");
        c.setVariable("practitionerRole", "Occupational Therapy Specialist");
        c.setVariable("address", "123 Healthcare Avenue, 75011 Paris, France");
        c.setVariable("phone", "+33 1 00 00 00 00");
        c.setVariable("phoneHref", "+33100000000");
        c.setVariable("firstName", "Maria");
        c.setVariable("reference", "OT-7K2M9P");
        c.setVariable("concern", "Sensory integration");
        c.setVariable("longDate", "Thursday 17 September 2026");
        c.setVariable("time", "09:30");
        c.setVariable("timezone", "Europe/Paris");
        c.setVariable("online", online);
        c.setVariable("meetingUrl", null);
        c.setVariable("location", online
                ? "Online consultation" : "123 Healthcare Avenue, 75011 Paris, France");
        return c;
    }
}
