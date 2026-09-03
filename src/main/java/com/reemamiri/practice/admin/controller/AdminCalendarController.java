package com.reemamiri.practice.admin.controller;

import com.reemamiri.practice.admin.dto.CalendarResponse;
import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.service.AppointmentService;
import com.reemamiri.practice.availability.service.AvailabilityAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendar events for a window.
 *
 * Always range-bounded: a month view asks for a month. There is no
 * endpoint that returns every appointment, because a calendar that
 * loads the whole table works fine for a fortnight and then does not.
 */
@Tag(name = "Admin calendar")
@RestController
@RequestMapping("/api/v1/admin/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final AppointmentService appointmentService;
    private final AvailabilityAdminService availabilityAdminService;

    @Operation(summary = "Appointments and blocked periods within a time window",
            description = "Blocked periods travel with the appointments so a holiday is "
                    + "visible in the calendar rather than looking like a quiet day.")
    @GetMapping
    public CalendarResponse calendar(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) ConsultationType consultationType) {

        List<AppointmentSummary> appointments =
                appointmentService.calendar(from, to, status, consultationType);

        return new CalendarResponse(appointments, availabilityAdminService.blockedBetween(from, to));
    }
}
