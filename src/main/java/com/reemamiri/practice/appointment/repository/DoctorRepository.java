package com.reemamiri.practice.appointment.repository;

import com.reemamiri.practice.appointment.entity.Doctor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorRepository extends JpaRepository<Doctor, UUID> {
    Optional<Doctor> findFirstByActiveTrueOrderByCreatedAtAsc();
}
