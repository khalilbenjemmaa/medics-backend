package com.reemamiri.practice.appointment.dto;

import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import java.time.Instant;
import java.util.UUID;

/** The single-appointment view, opened deliberately by the admin. */
public record AppointmentDetail(
        UUID id,
        String reference,
        UUID patientId,
        String patientName,
        String patientEmail,
        String patientPhone,
        String concern,
        ConsultationType consultationType,
        AppointmentStatus status,
        Instant startAt,
        Instant endAt,
        String meetingUrl,
        String patientMessage,
        Instant createdAt,
        Instant cancelledAt) {}
