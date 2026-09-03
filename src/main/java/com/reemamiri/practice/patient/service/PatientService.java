package com.reemamiri.practice.patient.service;

import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.mapper.AppointmentMapper;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.patient.dto.PatientDetail;
import com.reemamiri.practice.patient.dto.PatientNoteDto;
import com.reemamiri.practice.patient.dto.PatientNoteRequest;
import com.reemamiri.practice.patient.dto.PatientSummary;
import com.reemamiri.practice.patient.entity.Patient;
import com.reemamiri.practice.patient.entity.PatientNote;
import com.reemamiri.practice.patient.repository.PatientNoteRepository;
import com.reemamiri.practice.patient.repository.PatientRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patients, for the admin area only.
 *
 * Nothing here is reachable without authentication. Note content is
 * never written to the log — a note is the most sensitive text in the
 * system, and a log file is a copy of whatever is put into it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientNoteRepository noteRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentMapper appointmentMapper;

    @Transactional(readOnly = true)
    public Page<PatientSummary> search(String query, Pageable pageable) {
        String like = (query == null || query.isBlank())
                ? null : "%" + query.trim().toLowerCase() + "%";

        return patientRepository.search(like, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public PatientDetail get(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That patient"));

        List<AppointmentSummary> appointments = appointmentRepository.findByPatient(id).stream()
                .map(appointmentMapper::toSummary)
                .toList();

        List<PatientNoteDto> notes = noteRepository.findByPatientIdOrderByCreatedAtDesc(id).stream()
                .map(n -> new PatientNoteDto(n.getId(), n.getContent(), n.getAuthor(), n.getCreatedAt()))
                .toList();

        return new PatientDetail(
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                patient.getEmail(),
                patient.getPhone(),
                patient.getDateOfBirth(),
                patient.getCreatedAt(),
                appointments,
                notes);
    }

    @Transactional
    public PatientNoteDto addNote(UUID patientId, PatientNoteRequest request, String author) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> ApiException.notFound("That patient"));

        PatientNote note = new PatientNote();
        note.setPatient(patient);
        note.setContent(request.content());
        note.setAuthor(author);
        PatientNote saved = noteRepository.save(note);

        // The fact, never the content.
        log.info("Note added to a patient record");
        return new PatientNoteDto(saved.getId(), saved.getContent(), saved.getAuthor(), saved.getCreatedAt());
    }

    @Transactional
    public void deleteNote(UUID noteId) {
        if (!noteRepository.existsById(noteId)) {
            throw ApiException.notFound("That note");
        }
        noteRepository.deleteById(noteId);
    }

    private PatientSummary toSummary(Patient patient) {
        List<Appointment> appointments = appointmentRepository.findByPatient(patient.getId());
        Instant now = Instant.now();

        Instant last = appointments.stream()
                .filter(a -> a.getStartAt().isBefore(now))
                .map(Appointment::getStartAt)
                .max(Comparator.naturalOrder())
                .orElse(null);

        Instant next = appointments.stream()
                .filter(a -> a.getStartAt().isAfter(now) && a.getStatus().isBlocking())
                .map(Appointment::getStartAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new PatientSummary(
                patient.getId(),
                patient.fullName(),
                patient.getEmail(),
                patient.getPhone(),
                last,
                next,
                appointments.size());
    }
}
