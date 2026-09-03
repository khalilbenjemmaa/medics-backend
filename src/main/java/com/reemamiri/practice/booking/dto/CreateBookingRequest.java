package com.reemamiri.practice.booking.dto;

import com.reemamiri.practice.appointment.entity.ConsultationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking request.
 *
 * Only the start instant is accepted; the end is derived from the
 * configured slot duration. Letting a client choose its own end time
 * would let it book a three-hour appointment in a thirty-minute slot.
 */
public record CreateBookingRequest(
        @NotNull(message = "Choose a reason for the appointment.") UUID concernCategoryId,
        @NotNull(message = "Choose online or in-practice.") ConsultationType consultationType,
        @NotNull(message = "Choose a time.") Instant startAt,
        @NotNull @Valid PatientRequest patient,
        @Size(max = 2000) String patientMessage) {}
