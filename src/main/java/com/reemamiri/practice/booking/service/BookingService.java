package com.reemamiri.practice.booking.service;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.booking.dto.BookingResponse;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.config.AppProperties;
import com.reemamiri.practice.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Creates appointments.
 *
 * Three problems dominate this class, and each is solved in a specific
 * place rather than by being careful:
 *
 * 1. DOUBLE BOOKING. Two requests can read the same free slot before
 *    either writes. No amount of checking first fixes that, so the
 *    real guarantee is the appointment_no_overlap exclusion constraint
 *    in the database: an overlapping active appointment cannot be
 *    stored at all. The availability pre-check here exists only to
 *    give a good error message in the common case; the constraint is
 *    what makes the guarantee true. The violation is caught and
 *    translated into 409 SLOT_NO_LONGER_AVAILABLE.
 *
 * 2. RETRIES. A double-clicked button or a network retry must not
 *    produce two appointments. An Idempotency-Key is recorded in the
 *    same transaction as the appointment, so either both exist or
 *    neither does, and a repeat of the same key replays the original
 *    answer.
 *
 * 3. EXTERNAL FAILURE. Creating the meeting is a call to someone
 *    else's service and can fail or hang. It happens strictly AFTER
 *    the transaction commits, so a meeting outage can never roll back
 *    a confirmed appointment or leave a phantom hold on a slot. The
 *    appointment simply has no link yet, which is visible and fixable,
 *    rather than silently lost.
 *
 * The transactional work lives in {@link AppointmentCreationService}
 * rather than in a private method here. Spring's transactions are
 * proxy-based, so a call from one method of this class to another
 * would bypass the proxy and run with no transaction at all — silently,
 * which is the worst way for a booking system to lose its atomicity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private final AppointmentCreationService creationService;
    private final MeetingAttachmentService meetingAttachmentService;
    private final NotificationService notificationService;
    private final AppProperties properties;

    public BookingResponse book(CreateBookingRequest request, String idempotencyKey) {
        return book(request, idempotencyKey,
                AppointmentCreationService.Origin.PATIENT, false);
    }

    public BookingResponse book(CreateBookingRequest request, String idempotencyKey,
                                AppointmentCreationService.Origin origin,
                                boolean relaxAvailability) {
        Appointment appointment =
                creationService.create(request, idempotencyKey, origin, relaxAvailability);

        // Both steps run after the booking transaction has committed —
        // see (3) above. Neither can undo a confirmed appointment.
        meetingAttachmentService.attachIfOnline(appointment);
        notificationService.appointmentConfirmed(appointment);

        return toResponse(appointment);
    }

    private BookingResponse toResponse(Appointment appointment) {
        return new BookingResponse(
                appointment.getId(),
                appointment.getReference(),
                appointment.getStatus(),
                appointment.getConsultationType(),
                appointment.getStartAt(),
                appointment.getEndAt(),
                appointment.getMeetingUrl(),
                properties.doctorTimezone().getId(),
                appointment.getConcernCategory().getName());
    }
}
