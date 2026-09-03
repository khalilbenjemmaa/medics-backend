package com.reemamiri.practice.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Serialises the transactions competing for one slot.
 *
 * WHY THIS EXISTS: the exclusion constraint alone guarantees
 * correctness, but not liveness. When several transactions insert
 * overlapping rows at once, each holds index locks the others need, and
 * PostgreSQL resolves the cycle by killing one with a deadlock error —
 * observed reliably at eight concurrent bookings. A deadlock is not a
 * wrong answer, but "deadlock detected" is not something to show
 * someone booking an appointment.
 *
 * A transaction-scoped advisory lock keyed on the slot makes contenders
 * queue in a defined order instead, so exactly one proceeds at a time
 * and the rest get a clean SLOT_NO_LONGER_AVAILABLE. Because the key is
 * the slot itself, bookings for different times never block each other.
 *
 * The lock is released automatically when the transaction ends, in
 * either direction — there is no unlock to forget.
 *
 * This does not replace the constraint. It is the polite queue in front
 * of the guarantee.
 */
@Repository
public interface SlotLockRepository extends JpaRepository<com.reemamiri.practice.booking.entity.BookingIdempotency, java.util.UUID> {

    @Query(value = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))", nativeQuery = true)
    void lockSlot(@Param("key") String key);
}
