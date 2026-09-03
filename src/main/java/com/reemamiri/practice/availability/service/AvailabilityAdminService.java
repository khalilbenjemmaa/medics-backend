package com.reemamiri.practice.availability.service;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.appointment.service.DoctorProvider;
import com.reemamiri.practice.availability.dto.AvailabilityImpact;
import com.reemamiri.practice.availability.dto.BlockedPeriodDto;
import com.reemamiri.practice.availability.dto.BlockedPeriodRequest;
import com.reemamiri.practice.availability.dto.WeeklyAvailabilityDto;
import com.reemamiri.practice.availability.dto.WeeklyAvailabilityRequest;
import com.reemamiri.practice.availability.entity.BlockedPeriod;
import com.reemamiri.practice.availability.entity.WeeklyAvailability;
import com.reemamiri.practice.availability.repository.BlockedPeriodRepository;
import com.reemamiri.practice.availability.repository.WeeklyAvailabilityRepository;
import com.reemamiri.practice.common.exception.ApiException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing when the practice is open.
 *
 * Changing availability is the one admin action that can invalidate
 * something a patient already holds, so blocking a period reports the
 * appointments it now covers instead of silently stranding them. The
 * appointments are deliberately NOT cancelled automatically: deciding
 * what happens to a booked patient is the practitioner's call, not a
 * side effect of editing a calendar.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityAdminService {

    private final WeeklyAvailabilityRepository weeklyRepository;
    private final BlockedPeriodRepository blockedRepository;
    private final AppointmentRepository appointmentRepository;
    private final DoctorProvider doctorProvider;

    /* ---------------- weekly ---------------- */

    @Transactional(readOnly = true)
    public List<WeeklyAvailabilityDto> listWeekly() {
        return weeklyRepository
                .findByDoctorIdOrderByDayOfWeekValueAscStartTimeAsc(doctorProvider.getDoctorId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public WeeklyAvailabilityDto createWeekly(WeeklyAvailabilityRequest request) {
        validateInterval(request);
        WeeklyAvailability entity = new WeeklyAvailability();
        entity.setDoctor(doctorProvider.getDoctor());
        apply(entity, request);
        return toDto(saveWeekly(entity));
    }

    @Transactional
    public WeeklyAvailabilityDto updateWeekly(UUID id, WeeklyAvailabilityRequest request) {
        validateInterval(request);
        WeeklyAvailability entity = weeklyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That availability interval"));
        apply(entity, request);
        return toDto(saveWeekly(entity));
    }

    @Transactional
    public void deleteWeekly(UUID id) {
        if (!weeklyRepository.existsById(id)) {
            throw ApiException.notFound("That availability interval");
        }
        weeklyRepository.deleteById(id);
        log.info("Weekly availability interval removed");
    }

    private WeeklyAvailability saveWeekly(WeeklyAvailability entity) {
        try {
            return weeklyRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage())
                    .contains("weekly_availability_no_overlap")) {
                throw ApiException.conflict("OVERLAPPING_INTERVAL",
                        "That overlaps an existing interval on the same day.");
            }
            throw ex;
        }
    }

    private void apply(WeeklyAvailability entity, WeeklyAvailabilityRequest request) {
        entity.setDayOfWeek(request.dayOfWeek());
        entity.setStartTime(request.startTime());
        entity.setEndTime(request.endTime());
        entity.setActive(request.active() == null || request.active());
    }

    private void validateInterval(WeeklyAvailabilityRequest request) {
        if (!request.startTime().isBefore(request.endTime())) {
            throw ApiException.badRequest("INVALID_INTERVAL",
                    "The start time must be before the end time.");
        }
    }

    /* ---------------- blocked periods ---------------- */

    /**
     * Appointments that would sit inside a proposed blocked period, or
     * inside a working interval about to be removed.
     *
     * Read-only. Blocking time does not cancel what is already booked
     * inside it, and removing an interval does not either — silently
     * cancelling someone because a schedule was edited would be far
     * worse. But that leaves appointments stranded in hours just
     * declared closed, so the UI warns first using this.
     */
    @Transactional(readOnly = true)
    public AvailabilityImpact previewBlock(java.time.Instant startAt, java.time.Instant endAt) {
        List<Appointment> affected = appointmentRepository.findOverlapping(
                doctorProvider.getDoctorId(), startAt, endAt, AppointmentStatus.BLOCKING);
        return new AvailabilityImpact(affected.size(), affected.stream().map(this::summarise).toList());
    }

    /**
     * The same warning for a weekly interval being removed: every
     * future appointment that falls inside that recurring window.
     */
    @Transactional(readOnly = true)
    public AvailabilityImpact previewWeeklyRemoval(UUID intervalId) {
        WeeklyAvailability interval = weeklyRepository.findById(intervalId)
                .orElseThrow(() -> ApiException.notFound("That interval"));

        java.time.ZoneId zone = java.time.ZoneId.of(interval.getDoctor().getTimezone());
        java.time.Instant now = java.time.Instant.now();
        // A season ahead is far enough to catch anything realistically booked.
        java.time.Instant horizon = now.plus(java.time.Duration.ofDays(120));

        List<Appointment> affected = appointmentRepository
                .findOverlapping(interval.getDoctor().getId(), now, horizon,
                        AppointmentStatus.BLOCKING)
                .stream()
                .filter(appointment -> {
                    var local = appointment.getStartAt().atZone(zone);
                    return local.getDayOfWeek() == interval.getDayOfWeek()
                            && !local.toLocalTime().isBefore(interval.getStartTime())
                            && local.toLocalTime().isBefore(interval.getEndTime());
                })
                .toList();

        return new AvailabilityImpact(affected.size(), affected.stream().map(this::summarise).toList());
    }

    private com.reemamiri.practice.appointment.dto.AppointmentSummary summarise(Appointment a) {
        return new com.reemamiri.practice.appointment.dto.AppointmentSummary(
                a.getId(), a.getReference(), a.getPatient().fullName(), a.getPatient().getId(),
                a.getConcernCategory().getName(), a.getConsultationType(), a.getStatus(),
                a.getStartAt(), a.getEndAt(), a.getMeetingUrl());
    }

    /** Blocked periods overlapping a window, for the calendar. */
    @Transactional(readOnly = true)
    public List<BlockedPeriodDto> blockedBetween(java.time.Instant from, java.time.Instant to) {
        return blockedRepository.findOverlapping(doctorProvider.getDoctorId(), from, to)
                .stream()
                .map(b -> new BlockedPeriodDto(b.getId(), b.getStartAt(), b.getEndAt(), b.getReason()))
                .toList();
    }

    public List<BlockedPeriodDto> listBlocked() {
        return blockedRepository.findByDoctorIdOrderByStartAtAsc(doctorProvider.getDoctorId())
                .stream()
                .map(b -> new BlockedPeriodDto(b.getId(), b.getStartAt(), b.getEndAt(), b.getReason()))
                .toList();
    }

    @Transactional
    public BlockedPeriodDto createBlocked(BlockedPeriodRequest request) {
        validateBlocked(request);
        BlockedPeriod entity = new BlockedPeriod();
        entity.setDoctor(doctorProvider.getDoctor());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setReason(request.reason());

        warnAboutAffectedAppointments(request);

        BlockedPeriod saved = blockedRepository.save(entity);
        return new BlockedPeriodDto(saved.getId(), saved.getStartAt(), saved.getEndAt(), saved.getReason());
    }

    @Transactional
    public BlockedPeriodDto updateBlocked(UUID id, BlockedPeriodRequest request) {
        validateBlocked(request);
        BlockedPeriod entity = blockedRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That blocked period"));
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setReason(request.reason());
        BlockedPeriod saved = blockedRepository.save(entity);
        return new BlockedPeriodDto(saved.getId(), saved.getStartAt(), saved.getEndAt(), saved.getReason());
    }

    @Transactional
    public void deleteBlocked(UUID id) {
        if (!blockedRepository.existsById(id)) {
            throw ApiException.notFound("That blocked period");
        }
        blockedRepository.deleteById(id);
    }

    /**
     * @return appointments already booked inside a period about to be
     *         blocked, so the UI can warn before committing.
     */
    @Transactional(readOnly = true)
    public List<Appointment> appointmentsInPeriod(BlockedPeriodRequest request) {
        return appointmentRepository.findOverlapping(
                doctorProvider.getDoctorId(),
                request.startAt(),
                request.endAt(),
                AppointmentStatus.BLOCKING);
    }

    private void warnAboutAffectedAppointments(BlockedPeriodRequest request) {
        List<Appointment> affected = appointmentsInPeriod(request);
        if (!affected.isEmpty()) {
            // Left standing on purpose — see the class comment.
            log.warn("A blocked period covers {} existing appointment(s); "
                    + "they remain booked and need handling by hand.", affected.size());
        }
    }

    private void validateBlocked(BlockedPeriodRequest request) {
        if (!request.startAt().isBefore(request.endAt())) {
            throw ApiException.badRequest("INVALID_PERIOD",
                    "The start must be before the end.");
        }
    }

    private WeeklyAvailabilityDto toDto(WeeklyAvailability entity) {
        return new WeeklyAvailabilityDto(
                entity.getId(), entity.getDayOfWeek(),
                entity.getStartTime(), entity.getEndTime(), entity.isActive());
    }
}
