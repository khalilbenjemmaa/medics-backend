package com.reemamiri.practice.admin.service;

import com.reemamiri.practice.admin.dto.DashboardResponse;
import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.mapper.AppointmentMapper;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.appointment.service.AppointmentService;
import com.reemamiri.practice.appointment.service.DoctorProvider;
import com.reemamiri.practice.config.AppProperties;
import com.reemamiri.practice.contact.entity.ContactRequest;
import com.reemamiri.practice.contact.repository.ContactRequestRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The dashboard overview.
 *
 * Assembled server-side in one round trip. The alternative — the
 * browser firing six requests and counting the results — moves the
 * definition of "upcoming" into the client and makes the numbers
 * disagree the moment anything is paginated.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final int UPCOMING_LIMIT = 10;

    private final AppointmentRepository repository;
    private final AppointmentMapper mapper;
    private final AppointmentService appointmentService;
    private final DoctorProvider doctorProvider;
    private final ContactRequestRepository contactRepository;
    private final AppProperties properties;

    @Transactional(readOnly = true)
    public DashboardResponse overview() {
        UUID doctorId = doctorProvider.getDoctorId();
        Instant now = Instant.now();
        Instant todayStart = appointmentService.startOfToday();
        Instant tomorrowStart = appointmentService.startOfTomorrow();

        var active = AppointmentStatus.BLOCKING;

        long todayCount = repository.countInWindow(doctorId, todayStart, tomorrowStart, active);
        long upcomingCount = repository.countInWindow(
                doctorId, now, now.plus(java.time.Duration.ofDays(365)), active);
        long onlineCount = repository.countUpcomingByType(
                doctorId, ConsultationType.ONLINE, now, active);
        long onSiteCount = repository.countUpcomingByType(
                doctorId, ConsultationType.ON_SITE, now, active);

        List<AppointmentSummary> today = repository
                .findForCalendar(doctorId, todayStart, tomorrowStart, null, null)
                .stream()
                .map(mapper::toSummary)
                .toList();

        List<AppointmentSummary> upcoming = repository
                .findUpcoming(doctorId, now, active, PageRequest.of(0, UPCOMING_LIMIT))
                .stream()
                .map(mapper::toSummary)
                .toList();

        return new DashboardResponse(
                todayCount,
                upcomingCount,
                onlineCount,
                onSiteCount,
                upcoming.isEmpty() ? null : upcoming.get(0),
                today,
                upcoming,
                properties.doctorTimezone().getId(),
                attention(doctorId, now, active));
    }

    /**
     * What needs a person to act, as opposed to numbers to glance at.
     *
     * Three things the practitioner would otherwise have to notice for
     * herself: an online appointment with no link to send, an unread
     * message, and a past appointment still marked confirmed — which
     * means nobody recorded whether the person turned up.
     */
    private DashboardResponse.Attention attention(
            UUID doctorId, Instant now, Set<AppointmentStatus> active) {

        List<AppointmentSummary> missingLink = repository
                .findUpcoming(doctorId, now, active, PageRequest.of(0, 50))
                .stream()
                .filter(a -> a.getConsultationType() == ConsultationType.ONLINE)
                .filter(a -> a.getMeetingUrl() == null || a.getMeetingUrl().isBlank())
                .map(mapper::toSummary)
                .toList();

        // A grace period, so an appointment that has only just ended is
        // not immediately nagged about.
        Instant settled = now.minus(Duration.ofHours(2));
        List<AppointmentSummary> awaitingOutcome = repository
                .findOverlapping(doctorId, settled.minus(Duration.ofDays(30)), settled,
                        Set.of(AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING))
                .stream()
                .filter(a -> a.getEndAt().isBefore(settled))
                .map(mapper::toSummary)
                .toList();

        return new DashboardResponse.Attention(
                missingLink,
                contactRepository.countByStatus(ContactRequest.Status.NEW),
                awaitingOutcome);
    }
}
