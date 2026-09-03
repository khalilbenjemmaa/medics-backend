package com.reemamiri.practice.admin.controller;

import com.reemamiri.practice.patient.dto.PatientDetail;
import com.reemamiri.practice.patient.dto.PatientNoteDto;
import com.reemamiri.practice.patient.dto.PatientNoteRequest;
import com.reemamiri.practice.patient.dto.PatientSummary;
import com.reemamiri.practice.patient.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin patients")
@RestController
@RequestMapping("/api/v1/admin/patients")
@RequiredArgsConstructor
public class AdminPatientController {

    private final PatientService patientService;

    @Operation(summary = "Search patients by name, email or phone")
    @GetMapping
    public Page<PatientSummary> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int limited = Math.min(Math.max(size, 1), 100);
        return patientService.search(q,
                PageRequest.of(Math.max(page, 0), limited, Sort.by("lastName").ascending()));
    }

    @Operation(summary = "One patient, with appointment history and notes")
    @GetMapping("/{id}")
    public PatientDetail get(@PathVariable UUID id) {
        return patientService.get(id);
    }

    @Operation(summary = "Add a note to the patient file")
    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientNoteDto addNote(
            @PathVariable UUID id,
            @Valid @RequestBody PatientNoteRequest request,
            Principal principal) {
        return patientService.addNote(id, request, principal == null ? null : principal.getName());
    }

    @Operation(summary = "Delete a note")
    @DeleteMapping("/notes/{noteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteNote(@PathVariable UUID noteId) {
        patientService.deleteNote(noteId);
    }
}
