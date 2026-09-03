package com.reemamiri.practice.admin.controller;

import com.reemamiri.practice.availability.dto.AvailabilityImpact;
import com.reemamiri.practice.availability.dto.BlockedPeriodDto;
import com.reemamiri.practice.availability.dto.BlockedPeriodRequest;
import com.reemamiri.practice.availability.dto.WeeklyAvailabilityDto;
import com.reemamiri.practice.availability.dto.WeeklyAvailabilityRequest;
import com.reemamiri.practice.availability.service.AvailabilityAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin availability")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminAvailabilityController {

    private final AvailabilityAdminService service;

    /* ---------- weekly schedule ---------- */

    @Operation(summary = "The recurring weekly schedule")
    @GetMapping("/availability/weekly")
    public List<WeeklyAvailabilityDto> listWeekly() {
        return service.listWeekly();
    }

    @Operation(summary = "Add a working interval")
    @PostMapping("/availability/weekly")
    @ResponseStatus(HttpStatus.CREATED)
    public WeeklyAvailabilityDto createWeekly(@Valid @RequestBody WeeklyAvailabilityRequest request) {
        return service.createWeekly(request);
    }

    @Operation(summary = "Change a working interval")
    @PutMapping("/availability/weekly/{id}")
    public WeeklyAvailabilityDto updateWeekly(
            @PathVariable UUID id, @Valid @RequestBody WeeklyAvailabilityRequest request) {
        return service.updateWeekly(id, request);
    }

    @Operation(summary = "Remove a working interval")
    @DeleteMapping("/availability/weekly/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWeekly(@PathVariable UUID id) {
        service.deleteWeekly(id);
    }

    /* ---------- blocked periods ---------- */

    @Operation(summary = "Which appointments a proposed block would strand",
            description = "Call before creating a blocked period. Blocking does not cancel "
                    + "what is already booked inside it, so this is how the UI warns first.")
    @GetMapping("/blocked-periods/preview")
    public AvailabilityImpact previewBlock(
            @RequestParam Instant startAt, @RequestParam Instant endAt) {
        return service.previewBlock(startAt, endAt);
    }

    @Operation(summary = "Which appointments removing a weekly interval would strand")
    @GetMapping("/availability/weekly/{id}/impact")
    public AvailabilityImpact previewWeeklyRemoval(@PathVariable UUID id) {
        return service.previewWeeklyRemoval(id);
    }

    @Operation(summary = "Holidays, closures and blocked time")
    @GetMapping("/blocked-periods")
    public List<BlockedPeriodDto> listBlocked() {
        return service.listBlocked();
    }

    @Operation(summary = "Block a period",
            description = "Appointments already booked inside the period are left booked; "
                    + "what happens to them is the practitioner's decision, not a side effect.")
    @PostMapping("/blocked-periods")
    @ResponseStatus(HttpStatus.CREATED)
    public BlockedPeriodDto createBlocked(@Valid @RequestBody BlockedPeriodRequest request) {
        return service.createBlocked(request);
    }

    @Operation(summary = "Change a blocked period")
    @PutMapping("/blocked-periods/{id}")
    public BlockedPeriodDto updateBlocked(
            @PathVariable UUID id, @Valid @RequestBody BlockedPeriodRequest request) {
        return service.updateBlocked(id, request);
    }

    @Operation(summary = "Remove a blocked period")
    @DeleteMapping("/blocked-periods/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBlocked(@PathVariable UUID id) {
        service.deleteBlocked(id);
    }
}
