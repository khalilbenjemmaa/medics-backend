package com.reemamiri.practice.patient.dto;

import java.time.Instant;
import java.util.UUID;

public record PatientSummary(
        UUID id,
        String fullName,
        String email,
        String phone,
        Instant lastAppointmentAt,
        Instant nextAppointmentAt,
        int appointmentCount) {}
