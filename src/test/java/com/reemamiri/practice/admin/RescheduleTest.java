package com.reemamiri.practice.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reemamiri.practice.AbstractIntegrationTest;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import com.reemamiri.practice.appointment.service.AppointmentService;
import com.reemamiri.practice.availability.service.AvailabilityService;
import com.reemamiri.practice.appointment.service.DoctorProvider;
import com.reemamiri.practice.booking.dto.BookingResponse;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.dto.PatientRequest;
import com.reemamiri.practice.booking.service.BookingService;
import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.common.exception.SlotUnavailableException;
import com.reemamiri.practice.config.AppProperties;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Moving an appointment.
 *
 * A reschedule is a booking that also frees its old slot, so it has to
 * be held to the same standard: an admin must not be able to move an
 * appointment somewhere a patient could never have booked, nor on top
 * of someone else.
 */
class RescheduleTest extends AbstractIntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private BookingService bookingService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private ConcernCategoryRepository categoryRepository;
    @Autowired private DoctorProvider doctorProvider;
    @Autowired private AppProperties properties;

    @Test
    @DisplayName("an appointment moves, and its old slot becomes bookable again")
    void moveFreesTheOldSlot() {
        Instant original = slot(10, 0);
        Instant target = slot(11, 0);
        BookingResponse booking = book(original);

        appointmentService.reschedule(UUID.fromString(booking.appointmentId().toString()), target);

        var detail = appointmentService.get(booking.appointmentId());
        assertThat(detail.startAt()).isEqualTo(target);

        // The vacated slot is offered again, and the new one is not.
        assertThat(availabilityService.isSlotBookable(
                doctorProvider.getDoctorId(), original, original.plusSeconds(1800))).isTrue();
        assertThat(availabilityService.isSlotBookable(
                doctorProvider.getDoctorId(), target, target.plusSeconds(1800))).isFalse();
    }

    @Test
    @DisplayName("moving onto another appointment is refused")
    void cannotMoveOntoAnother() {
        BookingResponse first = book(slot(10, 0));
        book(slot(11, 0), "second@example.test");

        assertThatThrownBy(() -> appointmentService.reschedule(first.appointmentId(), slot(11, 0)))
                .isInstanceOf(SlotUnavailableException.class);

        // The original is untouched by the failed move.
        assertThat(appointmentService.get(first.appointmentId()).startAt()).isEqualTo(slot(10, 0));
    }

    @Test
    @DisplayName("moving outside working hours is refused")
    void cannotMoveOutsideWorkingHours() {
        BookingResponse booking = book(slot(10, 0));

        // Seeded Monday closes at 18:00.
        assertThatThrownBy(() -> appointmentService.reschedule(booking.appointmentId(), slot(21, 0)))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    @DisplayName("moving into the lunch gap is refused")
    void cannotMoveIntoTheGap() {
        BookingResponse booking = book(slot(10, 0));

        // Seeded Monday runs 09:00-12:00 and 14:00-18:00.
        assertThatThrownBy(() -> appointmentService.reschedule(booking.appointmentId(), slot(12, 30)))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    @DisplayName("moving to the time it already has is a no-op, not a conflict")
    void movingToItsOwnTimeSucceeds() {
        Instant when = slot(10, 0);
        BookingResponse booking = book(when);

        var detail = appointmentService.reschedule(booking.appointmentId(), when);
        assertThat(detail.startAt()).isEqualTo(when);
    }

    @Test
    @DisplayName("a cancelled appointment cannot be moved")
    void cannotMoveCancelled() {
        BookingResponse booking = book(slot(10, 0));
        appointmentService.cancel(booking.appointmentId());

        assertThatThrownBy(() -> appointmentService.reschedule(booking.appointmentId(), slot(11, 0)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cancelled");
    }

    @Test
    @DisplayName("the reference and status survive a move")
    void referenceAndStatusSurvive() {
        BookingResponse booking = book(slot(10, 0));
        var detail = appointmentService.reschedule(booking.appointmentId(), slot(11, 0));

        assertThat(detail.reference()).isEqualTo(booking.reference());
        assertThat(detail.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    /* ---------------- helpers ---------------- */

    private BookingResponse book(Instant startAt) {
        return book(startAt, "patient@example.test");
    }

    private BookingResponse book(Instant startAt, String email) {
        UUID categoryId = categoryRepository
                .findByActiveTrueOrderByDisplayOrderAscNameAsc().get(0).getId();
        return bookingService.book(
                new CreateBookingRequest(categoryId, ConsultationType.ON_SITE, startAt,
                        new PatientRequest("Test", "Patient", email, "+33600000000", null), null),
                UUID.randomUUID().toString());
    }

    private Instant slot(int hour, int minute) {
        LocalDate date = LocalDate.now(properties.doctorTimezone()).plusDays(2);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date.atTime(hour, minute).atZone(properties.doctorTimezone()).toInstant();
    }
}
