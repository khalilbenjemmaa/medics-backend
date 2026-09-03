package com.reemamiri.practice.availability.repository;

import com.reemamiri.practice.availability.entity.BlockedPeriod;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockedPeriodRepository extends JpaRepository<BlockedPeriod, UUID> {

    @Query("""
            select b from BlockedPeriod b
            where b.doctor.id = :doctorId
              and b.startAt < :windowEnd
              and b.endAt   > :windowStart
            order by b.startAt asc
            """)
    List<BlockedPeriod> findOverlapping(
            @Param("doctorId") UUID doctorId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);

    List<BlockedPeriod> findByDoctorIdOrderByStartAtAsc(UUID doctorId);
}
