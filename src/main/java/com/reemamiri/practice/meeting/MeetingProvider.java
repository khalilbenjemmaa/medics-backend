package com.reemamiri.practice.meeting;

import java.time.Instant;
import java.util.Optional;

/**
 * Creates a video meeting for an online appointment.
 *
 * Booking depends on this interface, never on a vendor. The Google
 * Calendar implementation drops in behind it without the booking
 * service changing: create the event, request a conference, return the
 * URL and the provider's event id.
 */
public interface MeetingProvider {

    /**
     * @return the created meeting, or empty when no provider is
     *         configured. Empty is a legitimate answer — the
     *         appointment is still valid, it simply has no link yet.
     */
    Optional<Meeting> createMeeting(MeetingRequest request);

    /** Best-effort cleanup. Never throws: a booking must not fail because tidy-up did. */
    void cancelMeeting(String externalEventId);

    record MeetingRequest(
            String summary,
            String description,
            Instant startAt,
            Instant endAt,
            String attendeeEmail,
            String attendeeName) {}

    record Meeting(String joinUrl, String externalEventId) {}
}
