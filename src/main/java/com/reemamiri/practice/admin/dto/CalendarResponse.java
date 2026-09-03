package com.reemamiri.practice.admin.dto;

import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import com.reemamiri.practice.availability.dto.BlockedPeriodDto;
import java.util.List;

/**
 * What the calendar draws for a window.
 *
 * Blocked periods travel alongside the appointments because a holiday
 * is otherwise invisible: the day simply looks quiet, which is exactly
 * when someone wonders why nothing is booked.
 */
public record CalendarResponse(
        List<AppointmentSummary> appointments,
        List<BlockedPeriodDto> blockedPeriods) {}
