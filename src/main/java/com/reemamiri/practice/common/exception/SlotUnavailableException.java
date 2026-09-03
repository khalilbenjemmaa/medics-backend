package com.reemamiri.practice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * The requested slot was taken between the client reading availability
 * and submitting the booking.
 *
 * Separate from a generic conflict because the frontend has a specific
 * recovery for it: refresh availability and ask the user to pick again.
 */
public class SlotUnavailableException extends ApiException {

    public static final String CODE = "SLOT_NO_LONGER_AVAILABLE";

    public SlotUnavailableException() {
        super(HttpStatus.CONFLICT, CODE, "This appointment slot is no longer available.");
    }
}
