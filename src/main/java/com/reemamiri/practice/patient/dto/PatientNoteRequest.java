package com.reemamiri.practice.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientNoteRequest(@NotBlank @Size(max = 10000) String content) {}
