package com.reemamiri.practice.appointment.entity;

import com.reemamiri.practice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The practitioner who owns every appointment.
 *
 * Exactly one row exists. It is modelled as an entity anyway so that
 * appointments have a real owner and a foreign key, rather than an
 * implicit assumption scattered through the code.
 */
@Entity
@Table(name = "doctor")
@Getter
@Setter
@NoArgsConstructor
public class Doctor extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false)
    private boolean active = true;

    public String fullName() {
        return firstName + " " + lastName;
    }
}
