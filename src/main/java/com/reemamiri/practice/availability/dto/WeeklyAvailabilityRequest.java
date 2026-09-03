package com.reemamiri.practice.availability.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * A recurring working interval.
 *
 * start &lt; end is checked in the service rather than by an annotation
 * so the failure names both fields; overlap with an existing interval
 * is caught by the database constraint.
 */
public record WeeklyAvailabilityRequest(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        Boolean active) {}
