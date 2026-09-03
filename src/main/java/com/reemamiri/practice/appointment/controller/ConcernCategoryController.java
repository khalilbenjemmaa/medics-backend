package com.reemamiri.practice.appointment.controller;

import com.reemamiri.practice.appointment.dto.ConcernCategoryDto;
import com.reemamiri.practice.appointment.repository.ConcernCategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The reasons a booking can be made.
 *
 * Public because the booking form needs the ids to submit against.
 * The frontend may still render its own wording; the ids are what the
 * backend validates.
 */
@Tag(name = "Booking")
@RestController
@RequiredArgsConstructor
public class ConcernCategoryController {

    private final ConcernCategoryRepository repository;

    @Operation(summary = "Active reasons for booking")
    @GetMapping("/api/v1/concern-categories")
    @Transactional(readOnly = true)
    public List<ConcernCategoryDto> list() {
        return repository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(c -> new ConcernCategoryDto(
                        c.getId(), c.getName(), c.getSlug(), c.getDescription()))
                .toList();
    }
}
