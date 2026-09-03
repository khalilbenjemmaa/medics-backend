package com.reemamiri.practice.appointment.entity;

import com.reemamiri.practice.common.entity.BaseEntity;
import com.reemamiri.practice.patient.entity.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A booked appointment.
 *
 * Times are Instants. A wall-clock string would be ambiguous across the
 * daylight-saving boundary — on the night the clocks go back, "02:30"
 * happens twice — and an appointment system that cannot tell those two
 * apart will eventually double-book one of them.
 *
 * Associations are LAZY throughout. Callers that need the patient or
 * category state so through a fetch join, so a calendar query does not
 * turn into one SELECT per appointment.
 */
@Entity
@Table(name = "appointment")
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseEntity {

    /**
     * Short human-quotable code, e.g. "OT-7K2M9P". Distinct from the
     * UUID: a patient reads this aloud on the phone.
     */
    @Column(nullable = false, length = 12, updatable = false)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "concern_category_id", nullable = false)
    private ConcernCategory concernCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "consultation_type", nullable = false, length = 16)
    private ConsultationType consultationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppointmentStatus status;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    /**
     * Populated only for ONLINE appointments, and only by the meeting
     * provider. Never constructed by hand: a fabricated meet.google.com
     * URL looks exactly like a real one until someone tries to join.
     */
    @Column(name = "meeting_url", length = 512)
    private String meetingUrl;

    @Column(name = "google_event_id", length = 255)
    private String googleEventId;

    @Column(name = "patient_message", columnDefinition = "text")
    private String patientMessage;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    /**
     * How the appointment came to exist. A booking taken over the phone
     * by the practitioner is a different thing from one a patient made
     * themselves, and the difference matters when reading a list.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 16)
    private CreatedBy createdBy = CreatedBy.PATIENT;

    public enum CreatedBy {
        PATIENT,
        ADMIN
    }

    public boolean isOnline() {
        return consultationType == ConsultationType.ONLINE;
    }

    public void cancel(Instant when) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = when;
    }
}
