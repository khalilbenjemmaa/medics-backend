package com.reemamiri.practice.availability;

import static org.assertj.core.api.Assertions.assertThat;

import com.reemamiri.practice.AbstractIntegrationTest;
import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import com.reemamiri.practice.appointment.service.DoctorProvider;
import com.reemamiri.practice.availability.dto.AvailabilityResponse;
import com.reemamiri.practice.availability.dto.DayAvailabilityDto;
import com.reemamiri.practice.availability.dto.SlotDto;
import com.reemamiri.practice.availability.entity.BlockedPeriod;
import com.reemamiri.practice.availability.repository.BlockedPeriodRepository;
import com.reemamiri.practice.availability.service.AvailabilityService;
import com.reemamiri.practice.common.util.ReferenceGenerator;
import com.reemamiri.practice.config.AppProperties;
import com.reemamiri.practice.patient.entity.Patient;
import com.reemamiri.practice.patient.repository.PatientRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The availability rules, against the seeded schedule:
 * Mon-Thu split days, Saturday morning, Friday and Sunday closed.
 */
class AvailabilityServiceTest extends AbstractIntegrationTest {

    @Autowired private AvailabilityService availabilityService;
    @Autowired private BlockedPeriodRepository blockedRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ConcernCategoryRepository categoryRepository;
    @Autowired private DoctorProvider doctorProvider;
    @Autowired private AppProperties properties;

    @Test
    @DisplayName("a working day offers slots across both intervals, and none over lunch")
    void workingDayHasSlotsButNotOverLunch() {
        LocalDate monday = next(DayOfWeek.MONDAY);
        List<SlotDto> slots = slotsOn(monday);

        assertThat(slots).isNotEmpty();

        List<LocalTime> localTimes = slots.stream()
                .map(s -> s.startAt().atZone(properties.doctorTimezone()).toLocalTime())
                .toList();

        // Seeded Monday is 09:00-12:00 and 14:00-18:00.
        assertThat(localTimes).contains(LocalTime.of(9, 0), LocalTime.of(11, 30), LocalTime.of(14, 0));
        // The gap between the intervals is not bookable.
        assertThat(localTimes).doesNotContain(LocalTime.of(12, 0), LocalTime.of(13, 0));
        // A 30-minute slot must fit entirely inside its interval.
        assertThat(localTimes).doesNotContain(LocalTime.of(18, 0));
    }

    @Test
    @DisplayName("a day with no working hours offers nothing")
    void closedDayHasNoSlots() {
        assertThat(slotsOn(next(DayOfWeek.FRIDAY))).isEmpty();
        assertThat(slotsOn(next(DayOfWeek.SUNDAY))).isEmpty();
    }

    @Test
    @DisplayName("a blocked period removes exactly the slots it covers")
    void blockedPeriodRemovesCoveredSlots() {
        LocalDate monday = next(DayOfWeek.MONDAY);
        assertThat(localTimesOn(monday)).contains(LocalTime.of(10, 0), LocalTime.of(10, 30));

        BlockedPeriod block = new BlockedPeriod();
        block.setDoctor(doctorProvider.getDoctor());
        block.setStartAt(at(monday, 10, 0));
        block.setEndAt(at(monday, 11, 0));
        block.setReason("Personal appointment");
        blockedRepository.saveAndFlush(block);

        List<LocalTime> after = localTimesOn(monday);
        assertThat(after).doesNotContain(LocalTime.of(10, 0), LocalTime.of(10, 30));
        // A half-open block leaves the slot starting at its end bookable.
        assertThat(after).contains(LocalTime.of(9, 30), LocalTime.of(11, 0));
    }

    @Test
    @DisplayName("a booked slot disappears, and comes back when cancelled")
    void bookedSlotIsHiddenAndReturnsAfterCancellation() {
        LocalDate monday = next(DayOfWeek.MONDAY);
        Appointment appointment = persistAppointment(at(monday, 10, 0));

        assertThat(localTimesOn(monday)).doesNotContain(LocalTime.of(10, 0));

        appointment.cancel(Instant.now());
        appointmentRepository.saveAndFlush(appointment);

        // A cancellation frees the slot: the overlap constraint and the
        // availability query both ignore cancelled rows.
        assertThat(localTimesOn(monday)).contains(LocalTime.of(10, 0));
    }

    @Test
    @DisplayName("slots inside the minimum lead time are never offered")
    void leadTimeIsRespected() {
        LocalDate today = LocalDate.now(properties.doctorTimezone());
        assertThat(slotsOn(today))
                .as("today is inside the 24-hour lead time")
                .isEmpty();
    }

    @Test
    @DisplayName("nothing beyond the booking horizon is offered")
    void horizonIsRespected() {
        LocalDate beyond = LocalDate.now(properties.doctorTimezone())
                .plusDays(properties.booking().maximumHorizonDays() + 7);
        // Land on a working day so only the horizon can be the reason.
        while (beyond.getDayOfWeek() != DayOfWeek.MONDAY) {
            beyond = beyond.plusDays(1);
        }
        assertThat(slotsOn(beyond)).isEmpty();
    }

    @Test
    @DisplayName("slots are the configured length and are reported in the doctor's zone")
    void slotDurationAndTimezone() {
        AvailabilityResponse response = response(next(DayOfWeek.MONDAY));

        assertThat(response.timezone()).isEqualTo(properties.doctorTimezone().getId());
        assertThat(response.slotDurationMinutes())
                .isEqualTo(properties.booking().slotDurationMinutes());

        SlotDto first = response.days().get(0).slots().get(0);
        assertThat(java.time.Duration.between(first.startAt(), first.endAt()).toMinutes())
                .isEqualTo(properties.booking().slotDurationMinutes());
    }

    /* ---------------- helpers ---------------- */

    private AvailabilityResponse response(LocalDate date) {
        return availabilityService.getAvailability(
                doctorProvider.getDoctorId(), date, date, ConsultationType.ON_SITE);
    }

    private List<SlotDto> slotsOn(LocalDate date) {
        return response(date).days().stream().map(DayAvailabilityDto::slots)
                .flatMap(List::stream).toList();
    }

    private List<LocalTime> localTimesOn(LocalDate date) {
        return slotsOn(date).stream()
                .map(s -> s.startAt().atZone(properties.doctorTimezone()).toLocalTime())
                .toList();
    }

    private Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(properties.doctorTimezone()).toInstant();
    }

    /** The next occurrence of a weekday, past the lead time. */
    private LocalDate next(DayOfWeek day) {
        LocalDate date = LocalDate.now(properties.doctorTimezone()).plusDays(2);
        while (date.getDayOfWeek() != day) {
            date = date.plusDays(1);
        }
        return date;
    }

    private Appointment persistAppointment(Instant start) {
        Patient patient = new Patient();
        patient.setFirstName("Booked");
        patient.setLastName("Patient");
        patient.setEmail("booked@example.test");
        patient.setPhone("+33600000000");
        patient = patientRepository.saveAndFlush(patient);

        Appointment appointment = new Appointment();
        appointment.setReference(ReferenceGenerator.generate());
        appointment.setDoctor(doctorProvider.getDoctor());
        appointment.setPatient(patient);
        appointment.setConcernCategory(
                categoryRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().get(0));
        appointment.setConsultationType(ConsultationType.ON_SITE);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setStartAt(start);
        appointment.setEndAt(start.plus(
                java.time.Duration.ofMinutes(properties.booking().slotDurationMinutes())));
        return appointmentRepository.saveAndFlush(appointment);
    }
}
