package com.reemamiri.practice.admin.controller;

import com.reemamiri.practice.appointment.dto.AppointmentDetail;
import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import com.reemamiri.practice.appointment.entity.AppointmentStatus;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin appointments")
@RestController
@RequestMapping("/api/v1/admin/appointments")
@RequiredArgsConstructor
public class AdminAppointmentController {

    private final AppointmentService appointmentService;

    public record StatusUpdate(@NotNull AppointmentStatus status) {}

    public record RescheduleRequest(@NotNull Instant startAt) {}

    @Operation(summary = "Search appointments")
    @GetMapping
    public Page<AppointmentSummary> list(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) ConsultationType consultationType,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Capped so a client cannot ask for the whole table in one page.
        int limited = Math.min(Math.max(size, 1), 100);
        return appointmentService.search(from, to, status, consultationType, q,
                PageRequest.of(Math.max(page, 0), limited, Sort.by("startAt").descending()));
    }

    @Operation(summary = "One appointment in full")
    @GetMapping("/{id}")
    public AppointmentDetail get(@PathVariable UUID id) {
        return appointmentService.get(id);
    }

    @Operation(summary = "Mark an appointment completed or a no-show")
    @PatchMapping("/{id}")
    public AppointmentDetail updateStatus(
            @PathVariable UUID id, @RequestBody StatusUpdate update) {
        return appointmentService.updateStatus(id, update.status());
    }

    @Operation(summary = "Move an appointment to a different time",
            description = "Validated exactly like a new booking: the target slot must be within "
                    + "working hours, unblocked and free. Returns 409 SLOT_NO_LONGER_AVAILABLE "
                    + "if something else holds it.")
    @PatchMapping("/{id}/reschedule")
    public AppointmentDetail reschedule(
            @PathVariable UUID id, @Valid @RequestBody RescheduleRequest request) {
        return appointmentService.reschedule(id, request.startAt());
    }

    @Operation(summary = "Cancel an appointment and free the slot",
            description = "The appointment is kept with a CANCELLED status rather than deleted, "
                    + "so the history survives. The slot becomes bookable again immediately.")
    @PatchMapping("/{id}/cancel")
    public AppointmentDetail cancel(@PathVariable UUID id) {
        return appointmentService.cancel(id);
    }
}
