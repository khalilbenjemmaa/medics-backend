package com.reemamiri.practice.contact.entity;

import com.reemamiri.practice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "contact_request")
@Getter
@Setter
@NoArgsConstructor
public class ContactRequest extends BaseEntity {

    public enum Status {
        NEW,
        READ,
        ARCHIVED
    }

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.NEW;

    /** When it was actually dealt with, as opposed to merely opened. */
    @Column(name = "handled_at")
    private java.time.Instant handledAt;
}
