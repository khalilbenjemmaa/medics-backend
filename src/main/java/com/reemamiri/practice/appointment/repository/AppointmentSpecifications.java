package com.reemamiri.practice.appointment.repository;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filters for the admin appointment search, composed per request.
 *
 * WHY NOT `(:param is null or column = :param)`:
 *
 * That idiom sends every filter as a bind parameter even when unused,
 * and PostgreSQL cannot infer the type of a bare NULL appearing only in
 * an `IS NULL` test. The result was a 500 —
 * "could not determine data type of parameter $2" — on the plainest
 * request the page makes: the unfiltered first page.
 *
 * Building the predicate list instead means an absent filter contributes
 * no SQL at all. Nothing to type, nothing to infer, and the planner sees
 * only the conditions that actually apply.
 */
public final class AppointmentSpecifications {

    private AppointmentSpecifications() {}

    public static Specification<Appointment> forDoctor(UUID doctorId) {
        return (root, query, cb) -> cb.equal(root.get("doctor").get("id"), doctorId);
    }

    public static Specification<Appointment> startsFrom(Instant from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startAt"), from);
    }

    public static Specification<Appointment> startsBefore(Instant to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThan(root.get("startAt"), to);
    }

    public static Specification<Appointment> hasStatus(AppointmentStatus status) {
        if (status == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Appointment> hasType(ConsultationType type) {
        if (type == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("consultationType"), type);
    }

    /** Free-text match across the patient's name and email. */
    public static Specification<Appointment> matches(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String like = "%" + query.trim().toLowerCase() + "%";
        return (root, criteriaQuery, cb) -> {
            var patient = root.get("patient");
            Predicate first = cb.like(cb.lower(patient.get("firstName")), like);
            Predicate last = cb.like(cb.lower(patient.get("lastName")), like);
            Predicate email = cb.like(cb.lower(patient.get("email")), like);
            return cb.or(first, last, email);
        };
    }
}
