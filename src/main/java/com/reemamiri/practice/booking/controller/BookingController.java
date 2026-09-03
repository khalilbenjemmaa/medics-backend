package com.reemamiri.practice.booking.controller;

import com.reemamiri.practice.booking.dto.BookingResponse;
import com.reemamiri.practice.booking.dto.CreateBookingRequest;
import com.reemamiri.practice.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Booking")
@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @Operation(summary = "Book an appointment",
            description = "Returns 409 SLOT_NO_LONGER_AVAILABLE if the slot was taken first. "
                    + "Send an Idempotency-Key so a retry cannot create a second appointment.")
    @PostMapping("/api/v1/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse book(
            @Valid @RequestBody CreateBookingRequest request,
            @Parameter(description = "Repeat-safe key for this booking attempt")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        return bookingService.book(request, idempotencyKey);
    }
}
