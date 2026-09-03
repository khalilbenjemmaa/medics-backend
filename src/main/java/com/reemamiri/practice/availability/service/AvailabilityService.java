package com.reemamiri.practice.availability.service;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.availability.dto.AvailabilityResponse;
import com.reemamiri.practice.availability.dto.DayAvailabilityDto;
import com.reemamiri.practice.availability.dto.SlotDto;
import com.reemamiri.practice.availability.entity.BlockedPeriod;
import com.reemamiri.practice.availability.entity.WeeklyAvailability;
import com.reemamiri.practice.availability.repository.BlockedPeriodRepository;
import com.reemamiri.practice.availability.repository.WeeklyAvailabilityRepository;
import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.config.AppProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out when the practice can actually be booked.
 *
 * This is the single source of truth for availability. Booking
 * validates against exactly this calculation, so a slot the API offers
 * is a slot the API will accept — the two cannot drift because they are
 * the same code path.
 *
 * A slot is offered only if all of these hold:
 *   1. it falls inside a recurring working interval for that weekday,
 *   2. it is not inside a blocked period,
 *   3. it does not overlap an appointment that still occupies time,
 *   4. it starts after the minimum lead time,
 *   5. it starts before the booking horizon,
 *   6. it fits entirely within its working interval.
 *
 * Timezone handling is the subtle part. Working hours are local wall
 * clock ("Monday 09:00"); everything stored is an instant. The
 * conversion happens per-day through the doctor's zone, so a
 * daylight-saving change shifts the UTC instant while leaving the local
 * hour where the practitioner expects it.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final WeeklyAvailabilityRepository weeklyRepository;
    private final BlockedPeriodRepository blockedRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppProperties properties;

    /** Hard ceiling on a single query, so one request cannot scan a decade. */
    private static final int MAX_RANGE_DAYS = 120;

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(
            UUID doctorId, LocalDate from, LocalDate to, ConsultationType type) {

        validateRange(from, to);

        ZoneId zone = properties.doctorTimezone();
        int slotMinutes = properties.booking().slotDurationMinutes();

        // One query per collection for the whole range, rather than per
        // day: a 90-day lookup should cost three round trips, not 270.
        Instant windowStart = from.atStartOfDay(zone).toInstant();
        Instant windowEnd = to.plusDays(1).atStartOfDay(zone).toInstant();

        Map<Integer, List<WeeklyAvailability>> intervalsByDay =
                weeklyRepository.findByDoctorIdAndActiveTrueOrderByDayOfWeekValueAscStartTimeAsc(doctorId)
                        .stream()
                        .collect(Collectors.groupingBy(w -> (int) w.getDayOfWeekValue()));

        List<BlockedPeriod> blocks =
                blockedRepository.findOverlapping(doctorId, windowStart, windowEnd);

        List<Appointment> booked = appointmentRepository.findOverlapping(
                doctorId, windowStart, windowEnd, AppointmentStatus.BLOCKING);

        Instant earliest = Instant.now().plus(Duration.ofHours(properties.booking().minimumLeadTimeHours()));
        LocalDate horizon = LocalDate.now(zone).plusDays(properties.booking().maximumHorizonDays());

        List<DayAvailabilityDto> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            List<SlotDto> slots = slotsForDay(
                    date, zone, slotMinutes, intervalsByDay, blocks, booked, earliest, horizon);
            days.add(new DayAvailabilityDto(date, !slots.isEmpty(), slots));
        }

        return new AvailabilityResponse(zone.getId(), from, to, slotMinutes, days);
    }

    private List<SlotDto> slotsForDay(
            LocalDate date,
            ZoneId zone,
            int slotMinutes,
            Map<Integer, List<WeeklyAvailability>> intervalsByDay,
            List<BlockedPeriod> blocks,
            List<Appointment> booked,
            Instant earliest,
            LocalDate horizon) {

        if (date.isAfter(horizon)) {
            return List.of();
        }

        List<WeeklyAvailability> intervals =
                intervalsByDay.getOrDefault(date.getDayOfWeek().getValue(), List.of());
        if (intervals.isEmpty()) {
            return List.of();
        }

        Duration slot = Duration.ofMinutes(slotMinutes);
        List<SlotDto> result = new ArrayList<>();

        for (WeeklyAvailability interval : intervals) {
            // Resolved per interval, per day: this is where a DST
            // transition is absorbed.
            ZonedDateTime cursor = LocalDateTime.of(date, interval.getStartTime()).atZone(zone);
            ZonedDateTime intervalEnd = LocalDateTime.of(date, interval.getEndTime()).atZone(zone);

            while (!cursor.plus(slot).isAfter(intervalEnd)) {
                Instant start = cursor.toInstant();
                Instant end = cursor.plus(slot).toInstant();

                if (!start.isBefore(earliest) && isFree(start, end, blocks, booked)) {
                    result.add(new SlotDto(start, end, true));
                }
                cursor = cursor.plus(slot);
            }
        }
        return result;
    }

    private boolean isFree(Instant start, Instant end, List<BlockedPeriod> blocks, List<Appointment> booked) {
        for (BlockedPeriod block : blocks) {
            if (block.covers(start, end)) {
                return false;
            }
        }
        for (Appointment appointment : booked) {
            // Half-open overlap, so back-to-back appointments are fine.
            if (appointment.getStartAt().isBefore(end) && appointment.getEndAt().isAfter(start)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether one specific slot can be booked right now.
     *
     * Booking calls this rather than re-deriving the rules, which is
     * what keeps "offered" and "accepted" in agreement. It is a
     * pre-check only: the authoritative guarantee is the database
     * overlap constraint, because anything checked here can be taken
     * before the insert lands.
     */
    @Transactional(readOnly = true)
    public boolean isSlotBookable(UUID doctorId, Instant start, Instant end) {
        ZoneId zone = properties.doctorTimezone();
        LocalDate date = start.atZone(zone).toLocalDate();

        AvailabilityResponse response = getAvailability(doctorId, date, date, null);
        return response.days().stream()
                .flatMap(day -> day.slots().stream())
                .anyMatch(s -> s.startAt().equals(start) && s.endAt().equals(end));
    }

    /**
     * Whether a window sits inside the working schedule and outside any
     * blocked period, ignoring existing appointments.
     *
     * Rescheduling needs this: the target slot legitimately looks busy
     * when the only thing in it is the appointment being moved.
     */
    @Transactional(readOnly = true)
    public boolean isWithinWorkingHours(UUID doctorId, Instant start, Instant end) {
        ZoneId zone = properties.doctorTimezone();
        LocalDate date = start.atZone(zone).toLocalDate();

        List<WeeklyAvailability> intervals =
                weeklyRepository.findByDoctorIdAndActiveTrueOrderByDayOfWeekValueAscStartTimeAsc(doctorId)
                        .stream()
                        .filter(w -> w.getDayOfWeek() == date.getDayOfWeek())
                        .toList();

        boolean inHours = intervals.stream().anyMatch(interval -> {
            Instant open = LocalDateTime.of(date, interval.getStartTime()).atZone(zone).toInstant();
            Instant close = LocalDateTime.of(date, interval.getEndTime()).atZone(zone).toInstant();
            return !start.isBefore(open) && !end.isAfter(close);
        });
        if (!inHours) {
            return false;
        }

        return blockedRepository.findOverlapping(doctorId, start, end).stream()
                .noneMatch(block -> block.covers(start, end));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("INVALID_RANGE", "Both 'from' and 'to' are required.");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("INVALID_RANGE", "'to' must not be before 'from'.");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw ApiException.badRequest(
                    "RANGE_TOO_LARGE", "Request at most " + MAX_RANGE_DAYS + " days at a time.");
        }
    }
}
