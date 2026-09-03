package com.reemamiri.practice.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Identity and audit columns, shared by every entity.
 *
 * The id is a UUID because these values appear in URLs the public can
 * see. A sequential integer would let anyone walk the appointment list
 * by incrementing a number.
 *
 * Timestamps are Instants, always UTC in the database. Local time
 * exists only at the edges: the doctor's zone interprets availability
 * rules, and the client renders whatever it likes.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Equality by identifier, and only once persisted. Two unsaved
     * entities are never equal, which keeps them safe to put in a Set
     * before they have an id.
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Objects.hash(getClass().getSimpleName());
    }
}
