package com.reemamiri.practice.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The current password is required even though the caller is already
 * authenticated. A token left open on an unattended screen should not
 * be enough to lock the real owner out of their own account.
 */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = 12, message = "Use at least 12 characters.")
        String newPassword) {}
