package com.reemamiri.practice.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Contact details captured at booking.
 *
 * Note what is not here: no diagnosis, no condition, no medical
 * history. An unauthenticated endpoint is the wrong place to invite
 * clinical detail, and none of it is needed to hold a slot.
 */
public record PatientRequest(
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        // Deliberately permissive: real numbers arrive in many shapes,
        // and rejecting a valid one loses a booking.
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[+0-9 ().-]{6,40}$",
                message = "Enter a valid phone number.") String phone,
        @Past(message = "Date of birth must be in the past.") LocalDate dateOfBirth) {}
