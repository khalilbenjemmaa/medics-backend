package com.reemamiri.practice.availability.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The whole availability answer for a date range.
 *
 * Deliberately complete: the frontend paints this and does not compute
 * availability itself. Anything it derived locally would be a second,
 * divergent implementation of the rules — and the one users would see
 * first when the two disagreed.
 */
public record AvailabilityResponse(
        String timezone,
        LocalDate from,
        LocalDate to,
        int slotDurationMinutes,
        List<DayAvailabilityDto> days) {}
