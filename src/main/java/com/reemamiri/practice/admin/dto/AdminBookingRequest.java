package com.reemamiri.practice.admin.dto;

import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.booking.dto.PatientRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * A booking taken by the practitioner, typically over the phone.
 *
 * `overrideAvailability` exists because the practitioner is allowed to
 * do things the public booking form is not: fit someone in outside
 * posted hours, or inside the lead time, when she has decided to. It
 * does NOT bypass the double-booking constraint — nothing does — so the
 * worst it can produce is an appointment outside normal hours, never
 * two people in the same slot.
 */
public record AdminBookingRequest(
        @NotNull UUID concernCategoryId,
        @NotNull ConsultationType consultationType,
        @NotNull Instant startAt,
        @NotNull @Valid PatientRequest patient,
        @Size(max = 2000) String patientMessage,
        boolean overrideAvailability) {}
