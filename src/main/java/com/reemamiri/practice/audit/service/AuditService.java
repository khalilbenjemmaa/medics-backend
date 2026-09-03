package com.reemamiri.practice.audit.service;

import com.reemamiri.practice.audit.entity.AuditEntry;
import com.reemamiri.practice.audit.repository.AuditRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records what the admin did.
 *
 * REQUIRES_NEW so an audit failure can never roll back the action it
 * describes. Losing an audit line is bad; losing a cancellation because
 * the audit insert failed would be worse, and much harder to explain to
 * the patient who was told they were cancelled.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String entityType, UUID entityId,
                       String entityRef, String detail) {
        try {
            AuditEntry entry = new AuditEntry();
            entry.setActor(currentActor());
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId);
            entry.setEntityRef(entityRef);
            entry.setDetail(detail);
            repository.save(entry);
        } catch (Exception ex) {
            // Never propagates: see the class comment.
            log.error("Could not write an audit entry for {} on {}", action, entityType, ex);
        }
    }

    private String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : String.valueOf(authentication.getName());
    }
}
