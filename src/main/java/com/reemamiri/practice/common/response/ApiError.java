package com.reemamiri.practice.common.response;

import java.time.Instant;
import java.util.Map;

/**
 * The one error shape every failing endpoint returns.
 *
 * {@code details} is field-keyed so a form can highlight the offending
 * input directly. Nothing internal is ever placed in here — no stack
 * traces, no SQL, no class names.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        Map<String, String> details) {

    public static ApiError of(int status, String code, String message, Map<String, String> details) {
        return new ApiError(Instant.now(), status, code, message,
                details == null || details.isEmpty() ? null : details);
    }
}
