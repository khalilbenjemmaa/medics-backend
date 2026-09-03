package com.reemamiri.practice.admin.controller;

import com.reemamiri.practice.admin.dto.AdminBookingRequest;
import com.reemamiri.practice.audit.service.AuditService;
import com.reemamiri.practice.booking.dto.BookingResponse;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.service.AppointmentCreationService;
import com.reemamiri.practice.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Booking on a patient's behalf.
 *
 * A practice takes bookings by phone constantly, and until now the only
 * way to record one was to open the public site and fill the form in as
 * if you were the patient.
 */
@Tag(name = "Admin appointments")
@RestController
@RequestMapping("/api/v1/admin/appointments")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingService bookingService;
    private final AuditService auditService;

    @Operation(summary = "Create an appointment on a patient's behalf",
            description = "Set overrideAvailability to fit someone in outside posted hours or "
                    + "inside the lead time. It never permits a double booking.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody AdminBookingRequest request) {

        BookingResponse response = bookingService.book(
                new CreateBookingRequest(
                        request.concernCategoryId(),
                        request.consultationType(),
                        request.startAt(),
                        request.patient(),
                        request.patientMessage()),
                // A fresh key per call: this is an operator pressing a
                // button, not a retryable client request.
                UUID.randomUUID().toString(),
                AppointmentCreationService.Origin.ADMIN,
                request.overrideAvailability());

        auditService.record("APPOINTMENT_CREATED", "Appointment",
                response.appointmentId(), response.reference(),
                "Booked by the practice for " + request.startAt()
                        + (request.overrideAvailability() ? " (availability overridden)" : ""));

        return response;
    }
}
