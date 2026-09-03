package com.reemamiri.practice.patient.repository;

import com.reemamiri.practice.patient.entity.Patient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByEmailIgnoreCase(String email);

    @Query("""
            select p from Patient p
            where :q is null
               or lower(p.firstName) like :q
               or lower(p.lastName)  like :q
               or lower(p.email)     like :q
               or p.phone            like :q
            """)
    Page<Patient> search(@Param("q") String q, Pageable pageable);
}
