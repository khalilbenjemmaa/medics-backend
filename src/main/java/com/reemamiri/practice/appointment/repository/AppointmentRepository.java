package com.reemamiri.practice.appointment.repository;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository
        extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    /**
     * Paged search, filtered by a composed Specification.
     *
     * The entity graph loads the patient and category in the same query
     * rather than one select per row, which a page of twenty would
     * otherwise cost. It is declared here rather than as a fetch join in
     * the Specification because a fetch join breaks the count query
     * Spring Data issues for pagination.
     */
    @Override
    @EntityGraph(attributePaths = {"patient", "concernCategory"})
    Page<Appointment> findAll(
            org.springframework.data.jpa.domain.Specification<Appointment> spec, Pageable pageable);

    Optional<Appointment> findByReference(String reference);

    /**
     * An appointment with the associations the booking response reads.
     *
     * Needed on the idempotent replay path: the stored record holds a
     * lazy proxy, and building a response from it after the transaction
     * has closed throws LazyInitializationException — so every retry
     * would fail with a 500 while the appointment itself was fine.
     */
    @Query("""
            select a from Appointment a
              join fetch a.concernCategory
              join fetch a.patient
            where a.id = :id
            """)
    Optional<Appointment> findByIdWithDetails(@Param("id") UUID id);

    /**
     * Appointments occupying any part of a window.
     *
     * Half-open overlap: {@code start < windowEnd AND end > windowStart}.
     * An appointment ending exactly when the window opens does not
     * overlap it, which is what makes back-to-back slots legal.
     */
    @Query("""
            select a from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.startAt < :windowEnd
              and a.endAt   > :windowStart
            order by a.startAt asc
            """)
    List<Appointment> findOverlapping(
            @Param("doctorId") UUID doctorId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd,
            @Param("statuses") Collection<AppointmentStatus> statuses);

    /**
     * Calendar query. Fetch-joins patient and category because the
     * calendar renders both for every row; without this a month view
     * would issue two extra selects per appointment.
     */
    @Query("""
            select a from Appointment a
              join fetch a.patient
              join fetch a.concernCategory
            where a.doctor.id = :doctorId
              and a.startAt < :windowEnd
              and a.endAt   > :windowStart
              and (:status is null or a.status = :status)
              and (:type   is null or a.consultationType = :type)
            order by a.startAt asc
            """)
    List<Appointment> findForCalendar(
            @Param("doctorId") UUID doctorId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd,
            @Param("status") AppointmentStatus status,
            @Param("type") ConsultationType type);

    @Query("""
            select count(a) from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.startAt >= :from and a.startAt < :to
            """)
    long countInWindow(
            @Param("doctorId") UUID doctorId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("""
            select count(a) from Appointment a
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.consultationType = :type
              and a.startAt >= :from
            """)
    long countUpcomingByType(
            @Param("doctorId") UUID doctorId,
            @Param("type") ConsultationType type,
            @Param("from") Instant from,
            @Param("statuses") Collection<AppointmentStatus> statuses);

    @Query("""
            select a from Appointment a
              join fetch a.patient
              join fetch a.concernCategory
            where a.doctor.id = :doctorId
              and a.status in :statuses
              and a.startAt >= :from
            order by a.startAt asc
            """)
    List<Appointment> findUpcoming(
            @Param("doctorId") UUID doctorId,
            @Param("from") Instant from,
            @Param("statuses") Collection<AppointmentStatus> statuses,
            Pageable pageable);

    @Query("""
            select a from Appointment a
              join fetch a.concernCategory
            where a.patient.id = :patientId
            order by a.startAt desc
            """)
    List<Appointment> findByPatient(@Param("patientId") UUID patientId);
}
