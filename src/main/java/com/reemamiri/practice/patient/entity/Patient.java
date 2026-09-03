package com.reemamiri.practice.patient.entity;

import com.reemamiri.practice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Someone who has booked or made contact.
 *
 * Only what booking genuinely needs. There is deliberately no field
 * here for a condition, a diagnosis or a medical history: the public
 * form must not invite people to type clinical detail into an
 * unauthenticated endpoint.
 */
@Entity
@Table(name = "patient")
@Getter
@Setter
@NoArgsConstructor
public class Patient extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
