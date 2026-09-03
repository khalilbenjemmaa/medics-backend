package com.reemamiri.practice.appointment.entity;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    COMPLETED,
    NO_SHOW;

    /**
     * Statuses that still occupy the calendar.
     *
     * This mirrors the WHERE clause of the appointment_no_overlap
     * constraint in V1__init.sql. The two must agree: if they drift,
     * availability will offer a slot the database then refuses to
     * store. Change one, change the other.
     */
    public static final Set<AppointmentStatus> BLOCKING =
            EnumSet.of(PENDING, CONFIRMED, COMPLETED);

    public boolean isBlocking() {
        return BLOCKING.contains(this);
    }
}
