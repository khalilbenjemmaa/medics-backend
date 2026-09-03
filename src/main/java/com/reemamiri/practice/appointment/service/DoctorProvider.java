package com.reemamiri.practice.appointment.service;

import com.reemamiri.practice.appointment.entity.Doctor;
import com.reemamiri.practice.appointment.repository.DoctorRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the practice's single practitioner.
 *
 * Every caller that needs "the doctor" goes through here, so the
 * one-practitioner assumption lives in exactly one place. If the
 * practice ever takes on a second therapist, this is the seam that
 * changes rather than every service that books against them.
 *
 * There is deliberately no public endpoint exposing this: a patient
 * never chooses a doctor.
 */
@Component
@RequiredArgsConstructor
public class DoctorProvider {

    private final DoctorRepository doctorRepository;

    @Transactional(readOnly = true)
    public Doctor getDoctor() {
        return doctorRepository.findFirstByActiveTrueOrderByCreatedAtAsc()
                .orElseThrow(() -> new IllegalStateException(
                        "No active practitioner is configured. Check that V2__seed.sql ran."));
    }

    public UUID getDoctorId() {
        return getDoctor().getId();
    }
}
