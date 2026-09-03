package com.reemamiri.practice.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One recorded administrative action.
 *
 * `detail` carries a short factual summary and never personal data: an
 * audit trail tends to be read by more people, and retained longer,
 * than the record it describes.
 */
@Entity
@Table(name = "audit_log")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry {

    @Id
    @GeneratedValue
    private UUID id;

    /** The admin's email. The only actor this system has. */
    @Column(nullable = false, length = 255)
    private String actor;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    /** e.g. an appointment reference, so the entry survives deletion. */
    @Column(name = "entity_ref", length = 64)
    private String entityRef;

    @Column(length = 500)
    private String detail;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
