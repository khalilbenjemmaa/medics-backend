package com.reemamiri.practice.availability.dto;

import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import java.util.List;

/**
 * Which existing appointments a proposed availability change would
 * strand.
 *
 * Blocking time does not cancel what is already booked inside it, and
 * removing a working interval does not either. That is the right
 * behaviour — silently cancelling someone's appointment because a
 * schedule was edited would be far worse — but it leaves appointments
 * the practitioner may have forgotten about, in hours she has just
 * declared closed. This is what lets the UI say so before she commits.
 */
public record AvailabilityImpact(int affectedCount, List<AppointmentSummary> affected) {}
