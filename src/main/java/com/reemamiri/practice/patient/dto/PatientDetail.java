package com.reemamiri.practice.patient.dto;

import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PatientDetail(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        Instant createdAt,
        List<AppointmentSummary> appointments,
        List<PatientNoteDto> notes) {}
