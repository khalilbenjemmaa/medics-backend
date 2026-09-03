package com.reemamiri.practice.appointment.repository;

import com.reemamiri.practice.appointment.entity.ConcernCategory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConcernCategoryRepository extends JpaRepository<ConcernCategory, UUID> {
    List<ConcernCategory> findByActiveTrueOrderByDisplayOrderAscNameAsc();
}
