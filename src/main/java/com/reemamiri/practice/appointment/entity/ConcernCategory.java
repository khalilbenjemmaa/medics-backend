package com.reemamiri.practice.appointment.entity;

import com.reemamiri.practice.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Why someone is booking.
 *
 * Deliberately a reason for attending, not a diagnosis, and not a claim
 * that the practice treats a named condition.
 */
@Entity
@Table(name = "concern_category")
@Getter
@Setter
@NoArgsConstructor
public class ConcernCategory extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;
}
