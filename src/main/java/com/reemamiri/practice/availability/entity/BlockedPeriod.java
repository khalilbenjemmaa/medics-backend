package com.reemamiri.practice.availability.entity;

import com.reemamiri.practice.appointment.entity.Doctor;
import com.reemamiri.practice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A span when the practice is closed — holiday, personal time, an
 * exceptional closure.
 *
 * Absolute instants rather than a recurring rule, because a block is a
 * specific stretch of real time. A full day is simply a block that
 * spans it; there is no separate all-day flag to keep consistent.
 */
@Entity
@Table(name = "blocked_period")
@Getter
@Setter
@NoArgsConstructor
public class BlockedPeriod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(length = 255)
    private String reason;

    /** Half-open: a block ending at 14:00 leaves 14:00 bookable. */
    public boolean covers(Instant slotStart, Instant slotEnd) {
        return startAt.isBefore(slotEnd) && endAt.isAfter(slotStart);
    }
}
