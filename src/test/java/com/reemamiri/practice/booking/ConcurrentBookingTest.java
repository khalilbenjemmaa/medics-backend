package com.reemamiri.practice.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.reemamiri.practice.AbstractIntegrationTest;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.dto.PatientRequest;
import com.reemamiri.practice.booking.service.BookingService;
import com.reemamiri.practice.common.exception.SlotUnavailableException;
import com.reemamiri.practice.config.AppProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The test this whole design exists for.
 *
 * Several requests attempt the identical slot at the same instant.
 * Exactly one must succeed. Two successes would mean two patients
 * arriving for the same appointment, which is the failure mode a
 * scheduling system cannot have.
 *
 * The threads are released together by a latch so they genuinely
 * contend, rather than running fast enough to accidentally serialise.
 */
class ConcurrentBookingTest extends AbstractIntegrationTest {

    @Autowired private BookingService bookingService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private ConcernCategoryRepository categoryRepository;
    @Autowired private AppProperties properties;

    private static final int THREADS = 8;

    @Test
    @DisplayName("only one of many simultaneous bookings for the same slot succeeds")
    void onlyOneBookingWinsTheSlot() throws Exception {
        UUID categoryId = categoryRepository
                .findByActiveTrueOrderByDisplayOrderAscNameAsc().get(0).getId();
        Instant slot = nextBookableSlot();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger conflicted = new AtomicInteger();

        List<Callable<Void>> attempts = java.util.stream.IntStream.range(0, THREADS)
                .<Callable<Void>>mapToObj(i -> () -> {
                    // Every thread waits here, then all are released at once.
                    startGate.await();
                    try {
                        bookingService.book(new CreateBookingRequest(
                                categoryId,
                                ConsultationType.ON_SITE,
                                slot,
                                new PatientRequest("Test", "Patient" + i,
                                        "patient" + i + "@example.test", "+33600000000", null),
                                null),
                                // Distinct keys: this tests slot contention,
                                // not idempotent replay.
                                UUID.randomUUID().toString());
                        succeeded.incrementAndGet();
                    } catch (SlotUnavailableException ex) {
                        conflicted.incrementAndGet();
                    }
                    return null;
                })
                .toList();

        List<Future<Void>> futures = attempts.stream().map(pool::submit).toList();
        startGate.countDown();
        for (Future<Void> future : futures) {
            future.get();
        }
        pool.shutdown();

        assertThat(succeeded.get())
                .as("exactly one booking may win the slot")
                .isEqualTo(1);
        assertThat(conflicted.get())
                .as("every other attempt must be rejected as a conflict")
                .isEqualTo(THREADS - 1);
        assertThat(appointmentRepository.findAll())
                .as("only one appointment may exist for the contested slot")
                .hasSize(1);
    }

    /** A slot inside seeded Monday hours, safely beyond the lead time. */
    private Instant nextBookableSlot() {
        LocalDate date = LocalDate.now(properties.doctorTimezone()).plusDays(1);
        while (date.getDayOfWeek().getValue() != 1) {
            date = date.plusDays(1);
        }
        return date.atTime(LocalTime.of(10, 0)).atZone(properties.doctorTimezone()).toInstant();
    }
}
