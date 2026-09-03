package com.reemamiri.practice.availability.controller;

import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.service.DoctorProvider;
import com.reemamiri.practice.availability.dto.AvailabilityResponse;
import com.reemamiri.practice.availability.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public availability.
 *
 * Returns fully-resolved slots so the client renders rather than
 * calculates. Any availability logic duplicated in the browser would be
 * a second implementation of these rules, and the one users would hit
 * first whenever the two disagreed.
 */
@Tag(name = "Availability")
@RestController
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final DoctorProvider doctorProvider;

    @Operation(summary = "Bookable slots in a date range")
    @GetMapping("/api/v1/availability")
    public AvailabilityResponse getAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) ConsultationType consultationType) {

        return availabilityService.getAvailability(
                doctorProvider.getDoctorId(), from, to, consultationType);
    }
}
