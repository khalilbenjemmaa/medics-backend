package com.reemamiri.practice.availability.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record BlockedPeriodRequest(
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Size(max = 255) String reason) {}
