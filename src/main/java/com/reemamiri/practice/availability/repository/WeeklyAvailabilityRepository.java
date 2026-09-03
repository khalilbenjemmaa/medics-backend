package com.reemamiri.practice.availability.repository;

import com.reemamiri.practice.availability.entity.WeeklyAvailability;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyAvailabilityRepository extends JpaRepository<WeeklyAvailability, UUID> {

    List<WeeklyAvailability> findByDoctorIdAndActiveTrueOrderByDayOfWeekValueAscStartTimeAsc(UUID doctorId);

    List<WeeklyAvailability> findByDoctorIdOrderByDayOfWeekValueAscStartTimeAsc(UUID doctorId);
}
