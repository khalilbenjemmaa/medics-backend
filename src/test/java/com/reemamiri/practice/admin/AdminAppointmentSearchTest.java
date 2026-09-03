package com.reemamiri.practice.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.reemamiri.practice.AbstractIntegrationTest;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import com.reemamiri.practice.appointment.service.AppointmentService;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.dto.PatientRequest;
import com.reemamiri.practice.booking.service.BookingService;
import com.reemamiri.practice.config.AppProperties;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * The admin appointment search, across every combination of filters.
 *
 * This exists because of a real failure. The previous implementation
 * used `(:param is null or column = :param)`, which sends unused filters
 * as untyped NULL binds; PostgreSQL cannot infer a type for those and
 * rejected the query outright. The endpoint returned 500 on the
 * plainest request the admin page makes — the unfiltered first page —
 * while the filtered variants happened to work.
 *
 * The earlier tests only asserted that the endpoint required
 * authentication, so nothing ever called it with real filters. These do.
 */
class AdminAppointmentSearchTest extends AbstractIntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private BookingService bookingService;
    @Autowired private ConcernCategoryRepository categoryRepository;
    @Autowired private AppProperties properties;

    private static final PageRequest FIRST_PAGE =
            PageRequest.of(0, 20, Sort.by("startAt").descending());

    @BeforeEach
    void seedAppointments() {
        book(ConsultationType.ON_SITE, slot(10, 0), "ada@example.test", "Ada", "Lovelace");
        book(ConsultationType.ONLINE, slot(10, 30), "grace@example.test", "Grace", "Hopper");
    }

    @Test
    @DisplayName("the unfiltered first page returns every appointment")
    void unfilteredSearchWorks() {
        var page = appointmentService.search(null, null, null, null, null, FIRST_PAGE);

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting("patientName")
                .containsExactlyInAnyOrder("Ada Lovelace", "Grace Hopper");
    }

    @Test
    @DisplayName("filtering by status returns only matches")
    void filterByStatus() {
        assertThat(appointmentService
                        .search(null, null, AppointmentStatus.CONFIRMED, null, null, FIRST_PAGE)
                        .getTotalElements())
                .isEqualTo(2);

        assertThat(appointmentService
                        .search(null, null, AppointmentStatus.CANCELLED, null, null, FIRST_PAGE)
                        .getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("filtering by consultation type returns only matches")
    void filterByType() {
        assertThat(appointmentService
                        .search(null, null, null, ConsultationType.ONLINE, null, FIRST_PAGE)
                        .getContent())
                .singleElement()
                .extracting("patientName")
                .isEqualTo("Grace Hopper");
    }

    @Test
    @DisplayName("a date range narrows the results, and each bound works alone")
    void filterByDateRange() {
        Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
        Instant future = Instant.now().plus(400, ChronoUnit.DAYS);

        assertThat(appointmentService.search(past, future, null, null, null, FIRST_PAGE)
                        .getTotalElements())
                .isEqualTo(2);

        // Each bound on its own leaves the other filter absent entirely,
        // which is the shape that used to fail.
        assertThat(appointmentService.search(past, null, null, null, null, FIRST_PAGE)
                        .getTotalElements())
                .isEqualTo(2);
        assertThat(appointmentService.search(null, future, null, null, null, FIRST_PAGE)
                        .getTotalElements())
                .isEqualTo(2);
        assertThat(appointmentService.search(future, null, null, null, null, FIRST_PAGE)
                        .getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("free text matches a name or an email, case-insensitively")
    void filterByQuery() {
        assertThat(appointmentService.search(null, null, null, null, "lovelace", FIRST_PAGE)
                        .getContent())
                .singleElement().extracting("patientName").isEqualTo("Ada Lovelace");

        assertThat(appointmentService.search(null, null, null, null, "GRACE@EXAMPLE", FIRST_PAGE)
                        .getContent())
                .singleElement().extracting("patientName").isEqualTo("Grace Hopper");

        assertThat(appointmentService.search(null, null, null, null, "nobody", FIRST_PAGE)
                        .getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("filters combine")
    void filtersCombine() {
        assertThat(appointmentService
                        .search(null, null, AppointmentStatus.CONFIRMED,
                                ConsultationType.ON_SITE, "ada", FIRST_PAGE)
                        .getTotalElements())
                .isEqualTo(1);

        // Same query, wrong type: the combination must exclude it.
        assertThat(appointmentService
                        .search(null, null, AppointmentStatus.CONFIRMED,
                                ConsultationType.ONLINE, "ada", FIRST_PAGE)
                        .getTotalElements())
                .isZero();
    }

    @Test
    @DisplayName("paging reports the right totals")
    void pagingWorks() {
        var page = appointmentService.search(
                null, null, null, null, null, PageRequest.of(0, 1, Sort.by("startAt")));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.isFirst()).isTrue();
        assertThat(page.isLast()).isFalse();
    }

    /* ---------------- helpers ---------------- */

    private void book(ConsultationType type, Instant startAt, String email,
                      String firstName, String lastName) {
        UUID categoryId = categoryRepository
                .findByActiveTrueOrderByDisplayOrderAscNameAsc().get(0).getId();
        bookingService.book(
                new CreateBookingRequest(categoryId, type, startAt,
                        new PatientRequest(firstName, lastName, email, "+33600000000", null),
                        null),
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
