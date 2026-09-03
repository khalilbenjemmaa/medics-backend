package com.reemamiri.practice.availability.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record WeeklyAvailabilityDto(
        UUID id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, boolean active) {}
