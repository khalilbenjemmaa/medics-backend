package com.reemamiri.practice.contact.dto;

import com.reemamiri.practice.contact.entity.ContactRequest;
import java.time.Instant;
import java.util.UUID;

/** A contact message as the admin inbox shows it. */
public record ContactSummary(
        UUID id,
        String name,
        String email,
        String phone,
        String message,
        ContactRequest.Status status,
        Instant createdAt,
        Instant handledAt) {

    public static ContactSummary from(ContactRequest entity) {
        return new ContactSummary(
                entity.getId(),
                entity.getFirstName() + " " + entity.getLastName(),
                entity.getEmail(),
                entity.getPhone(),
                entity.getMessage(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getHandledAt());
    }
}
