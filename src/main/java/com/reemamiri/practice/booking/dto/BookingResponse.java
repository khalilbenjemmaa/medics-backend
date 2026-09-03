package com.reemamiri.practice.booking.dto;

import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import java.time.Instant;
import java.util.UUID;

/**
 * What the confirmation screen renders.
 *
 * {@code meetingUrl} is null for on-site appointments, and also null
 * for online ones until a meeting provider is configured — never a
 * plausible-looking placeholder, which would send someone to a URL
 * that does not exist.
 */
public record BookingResponse(
        UUID appointmentId,
        String reference,
        AppointmentStatus status,
        ConsultationType consultationType,
        Instant startAt,
        Instant endAt,
        String meetingUrl,
        String timezone,
        String concernCategoryName) {}
