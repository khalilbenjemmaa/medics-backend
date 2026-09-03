package com.reemamiri.practice.patient.entity;

import com.reemamiri.practice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The seam for a future patient file.
 *
 * Kept minimal on purpose: enough structure that clinical notes,
 * assessments and documents can be added later without reshaping the
 * patient module, but not a speculative medical-record system built
 * before anyone has asked for one.
 *
 * Note content is admin-only and is never logged.
 */
@Entity
@Table(name = "patient_note")
@Getter
@Setter
@NoArgsConstructor
public class PatientNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(length = 120)
    private String author;
}
