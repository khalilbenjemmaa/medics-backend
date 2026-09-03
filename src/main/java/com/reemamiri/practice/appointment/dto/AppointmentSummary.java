package com.reemamiri.practice.appointment.dto;

import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import java.time.Instant;
import java.util.UUID;

/**
 * An appointment as the calendar and lists show it.
 *
 * Carries the patient's name because the admin needs to recognise the
 * appointment, but not their date of birth, message or notes — a list
 * endpoint should not ship more personal data than it renders.
 */
public record AppointmentSummary(
        UUID id,
        String reference,
        String patientName,
        UUID patientId,
        String concern,
        ConsultationType consultationType,
        AppointmentStatus status,
        Instant startAt,
        Instant endAt,
        String meetingUrl) {}
