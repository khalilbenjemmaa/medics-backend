package com.reemamiri.practice.booking.repository;

import com.reemamiri.practice.booking.entity.BookingIdempotency;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingIdempotencyRepository extends JpaRepository<BookingIdempotency, UUID> {
    Optional<BookingIdempotency> findByIdempotencyKey(String idempotencyKey);
}
