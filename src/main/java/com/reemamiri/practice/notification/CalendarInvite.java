package com.reemamiri.practice.notification;

import com.reemamiri.practice.appointment.entity.Appointment;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Builds an iCalendar (.ics) invitation for an appointment.
 *
 * Hand-written rather than pulled from a library: the spec surface
 * needed here is a single VEVENT, and the format's real difficulties
 * are its escaping and line-folding rules, both of which are handled
 * below and neither of which a dependency would save much work on.
 *
 * Times are emitted as UTC (the trailing Z), which every calendar
 * client resolves to the reader's own zone. Writing local times would
 * require also shipping a VTIMEZONE block and getting it right.
 */
public final class CalendarInvite {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private CalendarInvite() {}

    public static String build(Appointment appointment, String organiserName,
                               String organiserEmail, String location) {

        String summary = "Occupational therapy — " + appointment.getConcernCategory().getName();
        StringBuilder description = new StringBuilder("Reference ")
                .append(appointment.getReference());
        if (appointment.getMeetingUrl() != null && !appointment.getMeetingUrl().isBlank()) {
            description.append("\\n\\nJoin: ").append(appointment.getMeetingUrl());
        }

        String body = String.join("\r\n",
                "BEGIN:VCALENDAR",
                "VERSION:2.0",
                "PRODID:-//Reem Amiri Occupational Therapy//Booking//EN",
                "CALSCALE:GREGORIAN",
                "METHOD:PUBLISH",
                "BEGIN:VEVENT",
                // Stable and unique: a client updating an existing entry
                // matches on UID, so the reference must not change.
                "UID:" + appointment.getReference() + "@reemamiri",
                "DTSTAMP:" + STAMP.format(Instant.now()),
                "DTSTART:" + STAMP.format(appointment.getStartAt()),
                "DTEND:" + STAMP.format(appointment.getEndAt()),
                fold("SUMMARY:" + escape(summary)),
                fold("DESCRIPTION:" + escape(description.toString())),
                fold("LOCATION:" + escape(location)),
                fold("ORGANIZER;CN=" + escape(organiserName) + ":mailto:" + organiserEmail),
                "STATUS:CONFIRMED",
                "END:VEVENT",
                "END:VCALENDAR");

        return body + "\r\n";
    }

    /** Commas, semicolons and backslashes are structural in iCalendar. */
    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    /**
     * Lines must not exceed 75 octets; longer ones continue on the next
     * line prefixed by a space. A long address or summary silently
     * corrupts the file without this.
     */
    private static String fold(String line) {
        if (line.length() <= 73) {
            return line;
        }
        StringBuilder folded = new StringBuilder(line.substring(0, 73));
        int index = 73;
        while (index < line.length()) {
            int end = Math.min(index + 72, line.length());
            folded.append("\r\n ").append(line, index, end);
            index = end;
        }
        return folded.toString();
    }
}
