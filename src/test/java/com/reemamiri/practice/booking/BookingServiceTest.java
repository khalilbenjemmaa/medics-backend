package com.reemamiri.practice.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.reemamiri.practice.AbstractIntegrationTest;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import com.reemamiri.practice.booking.dto.BookingResponse;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.dto.PatientRequest;
import com.reemamiri.practice.booking.service.BookingService;
import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.common.exception.SlotUnavailableException;
import com.reemamiri.practice.config.AppProperties;
import com.reemamiri.practice.patient.repository.PatientRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingServiceTest extends AbstractIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ConcernCategoryRepository categoryRepository;
    @Autowired private AppProperties properties;

    @Test
    @DisplayName("an on-site booking is confirmed and carries no meeting link")
    void onSiteBooking() {
        BookingResponse response = book(ConsultationType.ON_SITE, slot(10, 0), null);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(response.consultationType()).isEqualTo(ConsultationType.ON_SITE);
        assertThat(response.meetingUrl()).isNull();
        assertThat(response.reference()).startsWith("OT-");
        assertThat(response.timezone()).isEqualTo(properties.doctorTimezone().getId());
    }

    @Test
    @DisplayName("an online booking is confirmed even with no meeting provider configured")
    void onlineBookingWithoutProvider() {
        BookingResponse response = book(ConsultationType.ONLINE, slot(10, 30), null);

        assertThat(response.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        // No provider is wired, so there is genuinely no link. What must
        // never happen is a fabricated one that looks real.
        assertThat(response.meetingUrl()).isNull();
    }

    @Test
    @DisplayName("booking the same slot twice is rejected as a conflict")
    void doubleBookingIsRejected() {
        Instant when = slot(11, 0);
        book(ConsultationType.ON_SITE, when, null);

        assertThatThrownBy(() -> book(ConsultationType.ON_SITE, when, null))
                .isInstanceOf(SlotUnavailableException.class);

        assertThat(appointmentRepository.findAll()).hasSize(1);
    }

    @Test
    @DisplayName("repeating a request with the same idempotency key returns the original booking")
    void idempotentRetryReturnsOriginal() {
        String key = UUID.randomUUID().toString();
        Instant when = slot(11, 30);

        BookingResponse first = book(ConsultationType.ON_SITE, when, key);
        BookingResponse second = book(ConsultationType.ON_SITE, when, key);

        assertThat(second.appointmentId()).isEqualTo(first.appointmentId());
        assertThat(second.reference()).isEqualTo(first.reference());
        assertThat(appointmentRepository.findAll())
                .as("a retry must not create a second appointment")
                .hasSize(1);
    }

    @Test
    @DisplayName("reusing an idempotency key for a different request is rejected")
    void idempotencyKeyReuseIsRejected() {
        String key = UUID.randomUUID().toString();
        book(ConsultationType.ON_SITE, slot(14, 0), key);

        // Same key, different slot: a client bug. Replying with the
        // earlier booking would hand back the wrong appointment.
        assertThatThrownBy(() -> book(ConsultationType.ON_SITE, slot(14, 30), key))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("idempotency key");
    }

    @Test
    @DisplayName("a slot in the past is rejected")
    void pastSlotIsRejected() {
        Instant past = Instant.now().minus(2, ChronoUnit.DAYS);
        assertThatThrownBy(() -> book(ConsultationType.ON_SITE, past, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a slot inside the lead time is rejected")
    void tooSoonIsRejected() {
        Instant soon = Instant.now().plus(1, ChronoUnit.HOURS);
        assertThatThrownBy(() -> book(ConsultationType.ON_SITE, soon, null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("a slot outside working hours is rejected even though it is in the future")
    void outsideWorkingHoursIsRejected() {
        // Seeded Monday closes at 18:00.
        assertThatThrownBy(() -> book(ConsultationType.ON_SITE, slot(21, 0), null))
                .isInstanceOf(SlotUnavailableException.class);
    }

    @Test
    @DisplayName("an unknown concern category is rejected")
    void unknownCategoryIsRejected() {
        assertThatThrownBy(() -> bookingService.book(new CreateBookingRequest(
                        UUID.randomUUID(), ConsultationType.ON_SITE, slot(15, 0),
                        patient(), null), null))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("a returning patient reuses their record rather than duplicating it")
    void returningPatientReusesRecord() {
        book(ConsultationType.ON_SITE, slot(15, 30), null);
        book(ConsultationType.ON_SITE, slot(16, 0), null);

        assertThat(patientRepository.findAll())
                .as("the same email must not create two patients")
                .hasSize(1);
        assertThat(appointmentRepository.findAll()).hasSize(2);
    }

    /* ---------------- helpers ---------------- */

    private BookingResponse book(ConsultationType type, Instant startAt, String key) {
        UUID categoryId = categoryRepository
                .findByActiveTrueOrderByDisplayOrderAscNameAsc().get(0).getId();
        return bookingService.book(
                new CreateBookingRequest(categoryId, type, startAt, patient(), "Some context"), key);
    }

    private PatientRequest patient() {
        return new PatientRequest("Alex", "Morgan", "alex.morgan@example.test", "+33600000000", null);
    }

    /** A time on the next Monday, which the seed opens 09:00-12:00 and 14:00-18:00. */
    private Instant slot(int hour, int minute) {
        LocalDate date = LocalDate.now(properties.doctorTimezone()).plusDays(2);
        while (date.getDayOfWeek() != DayOfWeek.MONDAY) {
            date = date.plusDays(1);
        }
        return date.atTime(hour, minute).atZone(properties.doctorTimezone()).toInstant();
    }
}
