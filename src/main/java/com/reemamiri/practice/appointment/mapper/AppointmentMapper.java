package com.reemamiri.practice.appointment.mapper;

import com.reemamiri.practice.appointment.dto.AppointmentDetail;
import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import com.reemamiri.practice.appointment.entity.Appointment;
import org.springframework.stereotype.Component;

/**
 * Entity to DTO.
 *
 * Written by hand rather than generated: the mapping is small, and the
 * decision in it — which personal fields a list endpoint is allowed to
 * carry — is worth reading explicitly rather than inferring from
 * annotations.
 */
@Component
public class AppointmentMapper {

    public AppointmentSummary toSummary(Appointment a) {
        return new AppointmentSummary(
                a.getId(),
                a.getReference(),
                a.getPatient().fullName(),
                a.getPatient().getId(),
                a.getConcernCategory().getName(),
                a.getConsultationType(),
                a.getStatus(),
                a.getStartAt(),
                a.getEndAt(),
                a.getMeetingUrl());
    }

    public AppointmentDetail toDetail(Appointment a) {
        return new AppointmentDetail(
                a.getId(),
                a.getReference(),
                a.getPatient().getId(),
                a.getPatient().fullName(),
                a.getPatient().getEmail(),
                a.getPatient().getPhone(),
                a.getConcernCategory().getName(),
                a.getConsultationType(),
                a.getStatus(),
                a.getStartAt(),
                a.getEndAt(),
                a.getMeetingUrl(),
                a.getPatientMessage(),
                a.getCreatedAt(),
                a.getCancelledAt());
    }
}
