package com.reemamiri.practice.meeting;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * The provider used when no meeting integration is configured.
 *
 * It returns no meeting rather than inventing one. A fabricated
 * meet.google.com URL is indistinguishable from a real one until a
 * patient tries to join and finds nothing there, so this deliberately
 * produces nothing and says so in the log.
 *
 * Replacing it means adding a bean implementing MeetingProvider;
 * @ConditionalOnMissingBean then steps this one aside.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(ignored = NoopMeetingProvider.class, value = MeetingProvider.class)
public class NoopMeetingProvider implements MeetingProvider {

    @Override
    public Optional<Meeting> createMeeting(MeetingRequest request) {
        log.info("No meeting provider configured; online appointment created without a link.");
        return Optional.empty();
    }

    @Override
    public void cancelMeeting(String externalEventId) {
        // Nothing to cancel.
    }
}
