package com.reemamiri.practice.availability.dto;

import java.time.LocalDate;
import java.util.List;

/** Slots grouped by the doctor's local date, which is how a calendar renders them. */
public record DayAvailabilityDto(LocalDate date, boolean hasAvailability, List<SlotDto> slots) {}
