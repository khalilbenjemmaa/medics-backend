package com.reemamiri.practice.appointment.service;

import com.reemamiri.practice.appointment.dto.AppointmentDetail;
import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.mapper.AppointmentMapper;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.appointment.repository.AppointmentSpecifications;
import com.reemamiri.practice.audit.service.AuditService;
import com.reemamiri.practice.availability.service.AvailabilityService;
import com.reemamiri.practice.booking.repository.SlotLockRepository;
import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.common.exception.SlotUnavailableException;
import com.reemamiri.practice.config.AppProperties;
import com.reemamiri.practice.meeting.MeetingProvider;
import com.reemamiri.practice.notification.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading, filtering and changing the status of appointments. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private final AppointmentRepository repository;
    private final AppointmentMapper mapper;
    private final DoctorProvider doctorProvider;
    private final MeetingProvider meetingProvider;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AvailabilityService availabilityService;
    private final SlotLockRepository slotLockRepository;
    private final AppProperties properties;

    @Transactional(readOnly = true)
    public List<AppointmentSummary> calendar(
            Instant from, Instant to, AppointmentStatus status, ConsultationType type) {
        if (from == null || to == null || to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "Provide a valid 'from' and 'to'.");
        }
        return repository
                .findForCalendar(doctorProvider.getDoctorId(), from, to, status, type)
                .stream()
                .map(mapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AppointmentSummary> search(
            Instant from, Instant to, AppointmentStatus status,
            ConsultationType type, String query, Pageable pageable) {

        // Only the filters actually supplied contribute any SQL; the
        // rest return a null Specification and are dropped by `and`.
        Specification<Appointment> spec =
                Specification.allOf(
                        AppointmentSpecifications.forDoctor(doctorProvider.getDoctorId()),
                        AppointmentSpecifications.startsFrom(from),
                        AppointmentSpecifications.startsBefore(to),
                        AppointmentSpecifications.hasStatus(status),
                        AppointmentSpecifications.hasType(type),
                        AppointmentSpecifications.matches(query));

        return repository.findAll(spec, pageable).map(mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public AppointmentDetail get(UUID id) {
        return mapper.toDetail(load(id));
    }

    /**
     * Cancels rather than deletes.
     *
     * The appointment stays on the record with a CANCELLED status: it
     * is history, and someone may need to see that it happened and
     * when. Because the overlap constraint ignores cancelled rows, the
     * slot becomes bookable again immediately.
     */
    @Transactional
    public AppointmentDetail cancel(UUID id) {
        Appointment appointment = load(id);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return mapper.toDetail(appointment);
        }
        appointment.cancel(Instant.now());
        repository.save(appointment);

        // Best-effort external cleanup; the cancellation stands either way.
        if (appointment.getGoogleEventId() != null) {
            try {
                meetingProvider.cancelMeeting(appointment.getGoogleEventId());
            } catch (Exception ex) {
                log.error("Could not cancel the external meeting for {}. "
                        + "The appointment is cancelled here and the event may need "
                        + "removing by hand.", appointment.getReference(), ex);
            }
        }

        auditService.record("APPOINTMENT_CANCELLED", "Appointment",
                appointment.getId(), appointment.getReference(),
                "Cancelled, freeing " + appointment.getStartAt());
        notificationService.appointmentCancelled(appointment);
        log.info("Appointment {} cancelled", appointment.getReference());
        return mapper.toDetail(appointment);
    }

    /**
     * Moves an appointment to a terminal state.
     *
     * Restricted to outcomes the admin genuinely records after the
     * fact. Re-opening a cancelled appointment is not offered: the slot
     * may well have been rebooked, and silently resurrecting it would
     * collide with whoever took it.
     */
    @Transactional
    public AppointmentDetail updateStatus(UUID id, AppointmentStatus status) {
        if (status != AppointmentStatus.COMPLETED && status != AppointmentStatus.NO_SHOW) {
            throw ApiException.badRequest("UNSUPPORTED_STATUS",
                    "Only COMPLETED and NO_SHOW can be set here. Use /cancel to cancel.");
        }
        Appointment appointment = load(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw ApiException.conflict("ALREADY_CANCELLED",
                    "A cancelled appointment cannot be marked " + status + ".");
        }
        appointment.setStatus(status);
        auditService.record("APPOINTMENT_STATUS_CHANGED", "Appointment",
                appointment.getId(), appointment.getReference(), "Marked " + status);

        return mapper.toDetail(repository.save(appointment));
    }

    /**
     * Moves an appointment to a different time.
     *
     * Runs the same checks a new booking does, and for the same reason:
     * a reschedule is a booking that happens to free its old slot. It
     * takes the slot advisory lock so it queues behind any booking in
     * flight for the target time, and it relies on the same exclusion
     * constraint to make an overlap impossible — the admin moving an
     * appointment must not be able to double-book where a patient
     * could not.
     *
     * Cancelled appointments are not movable. The slot they used may
     * well have been taken since.
     */
    @Transactional
    public AppointmentDetail reschedule(UUID id, Instant newStart) {
        Appointment appointment = load(id);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw ApiException.conflict("ALREADY_CANCELLED",
                    "A cancelled appointment cannot be moved. Book a new one instead.");
        }
        if (newStart.equals(appointment.getStartAt())) {
            return mapper.toDetail(appointment);
        }

        Instant newEnd = newStart.plus(
                Duration.ofMinutes(properties.booking().slotDurationMinutes()));
        UUID doctorId = appointment.getDoctor().getId();

        slotLockRepository.lockSlot(doctorId + "|" + newStart);

        // The appointment's own current slot would otherwise count as a
        // clash with itself, so availability is checked ignoring it.
        boolean free = availabilityService.isSlotBookable(doctorId, newStart, newEnd)
                || onlyClashIsSelf(doctorId, newStart, newEnd, appointment.getId());
        if (!free) {
            throw new SlotUnavailableException();
        }

        Instant previous = appointment.getStartAt();
        appointment.setStartAt(newStart);
        appointment.setEndAt(newEnd);

        try {
            repository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage())
                    .contains("appointment_no_overlap")) {
                throw new SlotUnavailableException();
            }
            throw ex;
        }

        log.info("Appointment {} moved from {} to {}",
                appointment.getReference(), previous, newStart);
        auditService.record("APPOINTMENT_RESCHEDULED", "Appointment",
                appointment.getId(), appointment.getReference(),
                "Moved from " + previous + " to " + newStart);
        notificationService.appointmentRescheduled(appointment);
        return mapper.toDetail(appointment);
    }

    /**
     * True when the only thing occupying the target window is this
     * appointment itself — which happens when the admin nudges a
     * booking within its own slot, or the availability rules exclude a
     * slot solely because this appointment sits there.
     */
    private boolean onlyClashIsSelf(UUID doctorId, Instant start, Instant end, UUID selfId) {
        List<Appointment> clashes = repository.findOverlapping(
                doctorId, start, end, AppointmentStatus.BLOCKING);
        if (clashes.size() != 1 || !clashes.get(0).getId().equals(selfId)) {
            return false;
        }
        // Still has to be a real working slot, not just an empty one.
        return availabilityService.isWithinWorkingHours(doctorId, start, end);
    }

    private Appointment load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That appointment"));
    }

    /** Start of today in the practice's own timezone, not the server's. */
    public Instant startOfToday() {
        return LocalDate.now(properties.doctorTimezone())
                .atStartOfDay(properties.doctorTimezone()).toInstant();
    }

    public Instant startOfTomorrow() {
        return LocalDate.now(properties.doctorTimezone()).plusDays(1)
                .atStartOfDay(properties.doctorTimezone()).toInstant();
    }
}
