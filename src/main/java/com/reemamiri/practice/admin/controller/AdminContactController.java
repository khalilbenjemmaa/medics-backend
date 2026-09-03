package com.reemamiri.practice.admin.controller;

import com.reemamiri.practice.audit.service.AuditService;
import com.reemamiri.practice.common.exception.ApiException;
import com.reemamiri.practice.contact.dto.ContactSummary;
import com.reemamiri.practice.contact.entity.ContactRequest;
import com.reemamiri.practice.contact.repository.ContactRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/**
 * The contact inbox.
 *
 * The public endpoint has always stored these messages; until now
 * nothing read them back, so every enquiry a patient sent went into the
 * database and was never seen. This is the missing half.
 */
@Tag(name = "Admin contact")
@RestController
@RequestMapping("/api/v1/admin/contact")
@RequiredArgsConstructor
public class AdminContactController {

    private final ContactRequestRepository repository;
    private final AuditService auditService;

    public record StatusUpdate(@NotNull ContactRequest.Status status) {}

    @Operation(summary = "List contact messages, newest first")
    @GetMapping
    @Transactional(readOnly = true)
    public Page<ContactSummary> list(
            @RequestParam(required = false) ContactRequest.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, Math.min(size, 100));
        var results = status == null
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByStatusOrderByCreatedAtDesc(status, pageable);

        return results.map(ContactSummary::from);
    }

    @Operation(summary = "How many messages are unread")
    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByStatus(ContactRequest.Status.NEW);
    }

    @Operation(summary = "Mark a message read, archived, or back to new")
    @PatchMapping("/{id}")
    @Transactional
    public ContactSummary updateStatus(
            @PathVariable UUID id, @Valid @RequestBody StatusUpdate update) {

        ContactRequest entity = repository.findById(id)
                .orElseThrow(() -> ApiException.notFound("That message"));

        entity.setStatus(update.status());
        // Only a terminal state counts as handled; re-opening clears it.
        entity.setHandledAt(update.status() == ContactRequest.Status.ARCHIVED
                ? Instant.now() : null);

        repository.save(entity);
        auditService.record("CONTACT_STATUS_CHANGED", "ContactRequest",
                entity.getId(), null, "Set to " + update.status());

        return ContactSummary.from(entity);
    }
}
