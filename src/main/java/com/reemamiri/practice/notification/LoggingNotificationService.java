package com.reemamiri.practice.notification;

import com.reemamiri.practice.appointment.entity.Appointment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Records that a notification would have been sent.
 *
 * Logs the reference only — never the patient's name, email or message.
 * A log file is a copy of whatever you put in it, and personal data
 * does not belong in one.
 */
@Slf4j
@Service
@ConditionalOnMissingBean(ignored = LoggingNotificationService.class, value = NotificationService.class)
public class LoggingNotificationService implements NotificationService {

    @Override
    public void appointmentConfirmed(Appointment appointment) {
        log.info("Notification due: confirmation for appointment {}", appointment.getReference());
    }

    @Override
    public void appointmentCancelled(Appointment appointment) {
        log.info("Notification due: cancellation for appointment {}", appointment.getReference());
    }

    @Override
    public void appointmentRescheduled(Appointment appointment) {
        log.info("Notification due: new time for appointment {}", appointment.getReference());
    }
}
