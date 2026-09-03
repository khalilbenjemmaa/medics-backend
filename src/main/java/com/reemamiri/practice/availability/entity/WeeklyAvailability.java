package com.reemamiri.practice.availability.entity;

import com.reemamiri.practice.appointment.entity.Doctor;
import com.reemamiri.practice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One recurring working interval, e.g. Monday 09:00-12:00.
 *
 * Stored as a LOCAL time and a day-of-week rather than as instants,
 * which is the whole point: "Monday at nine" must remain nine o'clock
 * after the clocks change, not shift to eight or ten. The doctor's
 * timezone converts these to real instants at query time.
 *
 * Several rows per day are expected — a split morning/afternoon day is
 * two intervals, and the gap between them is the lunch break.
 */
@Entity
@Table(name = "weekly_availability")
@Getter
@Setter
@NoArgsConstructor
public class WeeklyAvailability extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /** Persisted as the ISO number, 1 = Monday .. 7 = Sunday. */
    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeekValue;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private boolean active = true;

    public DayOfWeek getDayOfWeek() {
        return DayOfWeek.of(dayOfWeekValue);
    }

    public void setDayOfWeek(DayOfWeek day) {
        this.dayOfWeekValue = (short) day.getValue();
    }
}
