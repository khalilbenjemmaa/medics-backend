package com.reemamiri.practice.availability.dto;

import java.time.Instant;
import java.util.UUID;

public record BlockedPeriodDto(UUID id, Instant startAt, Instant endAt, String reason) {}
