package com.reemamiri.practice.patient.dto;

import java.time.Instant;
import java.util.UUID;

public record PatientNoteDto(UUID id, String content, String author, Instant createdAt) {}
