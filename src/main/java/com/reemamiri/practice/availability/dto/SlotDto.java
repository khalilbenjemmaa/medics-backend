package com.reemamiri.practice.availability.dto;

import java.time.Instant;

/**
 * One bookable slot.
 *
 * Serialised as ISO-8601 with an offset, so the client never has to
 * guess a zone. {@code available} is always true in public responses —
 * the field exists so the admin view can render taken slots too.
 */
public record SlotDto(Instant startAt, Instant endAt, boolean available) {}
