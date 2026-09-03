package com.reemamiri.practice.booking.service;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConcernCategory;
import com.reemamiri.practice.appointment.entity.Doctor;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import com.reemamiri.practice.appointment.service.DoctorProvider;
import com.reemamiri.practice.availability.service.AvailabilityService;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.dto.PatientRequest;
import com.reemamiri.practice.booking.entity.BookingIdempotency;
import com.reemamiri.practice.booking.repository.BookingIdempotencyRepository;
import com.reemamiri.practice.booking.repository.SlotLockRepository;
import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.common.exception.SlotUnavailableException;
import com.reemamiri.practice.common.util.ReferenceGenerator;
import com.reemamiri.practice.config.AppProperties;
import com.reemamiri.practice.patient.entity.Patient;
import com.reemamiri.practice.patient.repository.PatientRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The atomic part of booking: validate, persist, record idempotency.
 *
 * A separate bean so the transaction is applied by a real proxy rather
 * than by an intra-class call that Spring would ignore.
 *
 * Nothing slow happens inside this boundary — no HTTP, no mail, no
 * calendar API. The transaction lasts microseconds, which is what keeps
 * the row locks it takes from becoming a queue under load.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentCreationService {

    private final AppointmentRepository appointmentRepository;
    private final ConcernCategoryRepository categoryRepository;
    private final PatientRepository patientRepository;
    private final BookingIdempotencyRepository idempotencyRepository;
    private final SlotLockRepository slotLockRepository;
    private final AvailabilityService availabilityService;
    private final DoctorProvider doctorProvider;
    private final AppProperties properties;

    @Transactional
    public Appointment create(CreateBookingRequest request, String idempotencyKey) {
        return create(request, idempotencyKey, Origin.PATIENT, false);
    }

    /** Where a booking came from, and what it is therefore allowed to do. */
    public enum Origin {
        PATIENT,
        ADMIN
    }

    /**
     * @param relaxAvailability lets the practitioner fit someone in
     *        outside posted hours or inside the lead time. It never
     *        relaxes the overlap rule: that is enforced by a database
     *        constraint no caller can opt out of, so the worst this can
     *        produce is an unusual time, never a double booking.
     */
    @Transactional
    public Appointment create(CreateBookingRequest request, String idempotencyKey,
                              Origin origin, boolean relaxAvailability) {

        String fingerprint = fingerprint(request);
        boolean hasKey = idempotencyKey != null && !idempotencyKey.isBlank();

        if (hasKey) {
            Optional<BookingIdempotency> existing =
                    idempotencyRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                BookingIdempotency record = existing.get();
                // Same key with a different payload is a client bug, not
                // a retry. Replying with the earlier appointment would
                // hand back someone else's booking.
                if (!record.getRequestFingerprint().equals(fingerprint)) {
                    throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED",
                            "This idempotency key was already used for a different request.");
                }
                log.info("Replaying booking for a repeated idempotency key");
                // Re-read with associations: the stored reference is a
                // lazy proxy, and the response is built after this
                // transaction closes.
                return appointmentRepository
                        .findByIdWithDetails(record.getAppointment().getId())
                        .orElseThrow(() -> ApiException.notFound("That appointment"));
            }
        }

        Doctor doctor = doctorProvider.getDoctor();
        ConcernCategory category = categoryRepository.findById(request.concernCategoryId())
                .orElseThrow(() -> ApiException.badRequest(
                        "INVALID_CATEGORY", "That reason for booking is not available."));

        if (!category.isActive()) {
            throw ApiException.badRequest(
                    "INACTIVE_CATEGORY", "That reason for booking is no longer available.");
        }

        Instant startAt = request.startAt();
        Instant endAt = startAt.plus(Duration.ofMinutes(properties.booking().slotDurationMinutes()));

        if (!relaxAvailability) {
            validateTiming(startAt);
        }

        // Queue behind anyone already booking this exact slot. Held for
        // the rest of the transaction and released with it. See
        // SlotLockRepository for why this is needed on top of the
        // constraint.
        slotLockRepository.lockSlot(doctor.getId() + "|" + startAt);

        // A friendly pre-check that produces a good error in the common
        // case. It is explicitly NOT the guarantee: between this line
        // and the insert, another request can take the slot. The
        // database constraint below is what makes double booking
        // impossible.
        if (!relaxAvailability && !availabilityService.isSlotBookable(doctor.getId(), startAt, endAt)) {
            throw new SlotUnavailableException();
        }

        Patient patient = upsertPatient(request.patient());

        Appointment appointment = new Appointment();
        appointment.setReference(ReferenceGenerator.generate());
        appointment.setDoctor(doctor);
        appointment.setPatient(patient);
        appointment.setConcernCategory(category);
        appointment.setConsultationType(request.consultationType());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setStartAt(startAt);
        appointment.setEndAt(endAt);
        appointment.setPatientMessage(request.patientMessage());
        appointment.setCreatedBy(origin == Origin.ADMIN
                ? Appointment.CreatedBy.ADMIN : Appointment.CreatedBy.PATIENT);

        try {
            // saveAndFlush forces the exclusion constraint to be checked
            // here, so a lost race is caught in this method rather than
            // erupting from a commit somewhere up the stack.
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (isOverlapViolation(ex)) {
                log.info("Booking lost the race for slot {}", startAt);
                throw new SlotUnavailableException();
            }
            throw ex;
        }

        if (hasKey) {
            BookingIdempotency record = new BookingIdempotency();
            record.setIdempotencyKey(idempotencyKey);
            record.setRequestFingerprint(fingerprint);
            record.setAppointment(appointment);
            idempotencyRepository.saveAndFlush(record);
        }

        log.info("Appointment {} created ({}) at {}",
                appointment.getReference(), appointment.getConsultationType(), startAt);
        return appointment;
    }

    /**
     * A returning patient books against their existing record rather
     * than accumulating a duplicate per appointment, which is what
     * makes the admin's patient history worth having.
     */
    private Patient upsertPatient(PatientRequest request) {
        String email = request.email().trim().toLowerCase();
        Patient patient = patientRepository.findByEmailIgnoreCase(email).orElseGet(Patient::new);

        patient.setFirstName(request.firstName().trim());
        patient.setLastName(request.lastName().trim());
        patient.setEmail(email);
        patient.setPhone(request.phone().trim());
        if (request.dateOfBirth() != null) {
            patient.setDateOfBirth(request.dateOfBirth());
        }
        return patientRepository.save(patient);
    }

    private void validateTiming(Instant startAt) {
        Instant earliest = Instant.now()
                .plus(Duration.ofHours(properties.booking().minimumLeadTimeHours()));
        if (startAt.isBefore(earliest)) {
            throw ApiException.unprocessable("TOO_SOON",
                    "Appointments must be booked at least "
                            + properties.booking().minimumLeadTimeHours() + " hours ahead.");
        }
        Instant horizon = Instant.now()
                .plus(Duration.ofDays(properties.booking().maximumHorizonDays()));
        if (startAt.isAfter(horizon)) {
            throw ApiException.unprocessable("TOO_FAR_AHEAD",
                    "Appointments cannot be booked more than "
                            + properties.booking().maximumHorizonDays() + " days ahead.");
        }
    }

    private boolean isOverlapViolation(DataIntegrityViolationException ex) {
        return String.valueOf(ex.getMostSpecificCause().getMessage())
                .contains("appointment_no_overlap");
    }

    /**
     * Identifies a booking payload so a repeated Idempotency-Key can be
     * checked against it. Hashed rather than stored: the payload holds
     * personal data with no reason to sit in a second table.
     */
    private String fingerprint(CreateBookingRequest request) {
        String raw = String.join("|",
                String.valueOf(request.concernCategoryId()),
                String.valueOf(request.consultationType()),
                String.valueOf(request.startAt()),
                request.patient().email().trim().toLowerCase());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
