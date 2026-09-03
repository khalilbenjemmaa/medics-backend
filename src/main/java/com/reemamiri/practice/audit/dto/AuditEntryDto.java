package com.reemamiri.practice.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditEntryDto(
        UUID id,
        String actor,
        String action,
        String entityType,
        UUID entityId,
        String entityRef,
        String detail,
        Instant createdAt) {}
