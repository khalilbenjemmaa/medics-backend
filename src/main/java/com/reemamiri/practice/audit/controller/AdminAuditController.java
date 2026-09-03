package com.reemamiri.practice.audit.controller;

import com.reemamiri.practice.audit.dto.AuditEntryDto;
import com.reemamiri.practice.audit.repository.AuditRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only history of administrative actions. */
@Tag(name = "Admin audit")
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AdminAuditController {

    private final AuditRepository repository;

    @Operation(summary = "Administrative actions, newest first")
    @GetMapping
    @Transactional(readOnly = true)
    public Page<AuditEntryDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        return repository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)))
                .map(e -> new AuditEntryDto(e.getId(), e.getActor(), e.getAction(),
                        e.getEntityType(), e.getEntityId(), e.getEntityRef(),
                        e.getDetail(), e.getCreatedAt()));
    }
}
