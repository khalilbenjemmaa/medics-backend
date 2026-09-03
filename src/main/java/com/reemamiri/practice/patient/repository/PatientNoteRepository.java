package com.reemamiri.practice.patient.repository;

import com.reemamiri.practice.patient.entity.PatientNote;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientNoteRepository extends JpaRepository<PatientNote, UUID> {
    List<PatientNote> findByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
