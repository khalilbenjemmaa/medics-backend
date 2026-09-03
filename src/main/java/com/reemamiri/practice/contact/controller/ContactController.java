package com.reemamiri.practice.contact.controller;

import com.reemamiri.practice.contact.dto.ContactRequestDto;
import com.reemamiri.practice.contact.entity.ContactRequest;
import com.reemamiri.practice.contact.repository.ContactRequestRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Contact")
@RestController
@RequiredArgsConstructor
@Slf4j
public class ContactController {

    private final ContactRequestRepository repository;

    @Operation(summary = "Send a message to the practice")
    @PostMapping("/api/v1/contact")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional
    public void submit(@Valid @RequestBody ContactRequestDto request) {
        ContactRequest entity = new ContactRequest();
        entity.setFirstName(request.firstName().trim());
        entity.setLastName(request.lastName().trim());
        entity.setEmail(request.email().trim().toLowerCase());
        entity.setPhone(request.phone());
        entity.setMessage(request.message());
        repository.save(entity);

        // The message may contain anything the sender chose to type,
        // so only the fact of it is recorded.
        log.info("Contact request received");
    }
}
