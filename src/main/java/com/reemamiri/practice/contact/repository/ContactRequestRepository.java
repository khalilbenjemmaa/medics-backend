package com.reemamiri.practice.contact.repository;

import com.reemamiri.practice.contact.entity.ContactRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, UUID> {

    Page<ContactRequest> findByStatusOrderByCreatedAtDesc(
            ContactRequest.Status status, Pageable pageable);

    Page<ContactRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(ContactRequest.Status status);
}
