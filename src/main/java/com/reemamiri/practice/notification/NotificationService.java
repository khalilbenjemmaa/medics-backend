package com.reemamiri.practice.notification;

import com.reemamiri.practice.appointment.entity.Appointment;

/**
 * Outbound notifications.
 *
 * An interface with a logging implementation, not a mail integration:
 * no provider has been chosen, and wiring a fake SMTP client into the
 * booking path would look like working email until the day someone
 * relied on it. Booking depends on this abstraction, so a real provider
 * is added by supplying a bean.
 */
public interface NotificationService {

    void appointmentConfirmed(Appointment appointment);

    void appointmentCancelled(Appointment appointment);

    void appointmentRescheduled(Appointment appointment);
}
