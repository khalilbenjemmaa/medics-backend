package com.reemamiri.practice.booking.service;

import com.reemamiri.practice.appointment.entity.Appointment;
import com.reemamiri.practice.appointment.entity.ConsultationType;
import com.reemamiri.practice.appointment.repository.AppointmentRepository;
import com.reemamiri.practice.meeting.MeetingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attaches a video meeting to an already-committed online appointment.
 *
 * Runs in its own transaction, after booking has committed. That
 * ordering is the whole point: a meeting provider outage must never
 * roll back a confirmed appointment, nor leave a phantom hold on a slot
 * that a retry then cannot rebook.
 *
 * Failures are logged and swallowed. The appointment is genuinely
 * booked either way, and telling a patient otherwise because a calendar
 * API timed out would be false. An appointment visibly missing its link
 * is a problem the admin can see and fix.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeetingAttachmentService {

    private final AppointmentRepository appointmentRepository;
    private final MeetingProvider meetingProvider;

    @Transactional
    public void attachIfOnline(Appointment appointment) {
        if (appointment.getConsultationType() != ConsultationType.ONLINE) {
            return;
        }
        try {
            Appointment managed = appointmentRepository.findById(appointment.getId()).orElseThrow();

            meetingProvider.createMeeting(new MeetingProvider.MeetingRequest(
                            "Occupational therapy appointment",
                            "Reference " + managed.getReference(),
                            managed.getStartAt(),
                            managed.getEndAt(),
                            managed.getPatient().getEmail(),
                            managed.getPatient().fullName()))
                    .ifPresent(meeting -> {
                        managed.setMeetingUrl(meeting.joinUrl());
                        managed.setGoogleEventId(meeting.externalEventId());
                        appointmentRepository.save(managed);
                        // Mirror onto the caller's instance so the
                        // response carries the link without a re-read.
                        appointment.setMeetingUrl(meeting.joinUrl());
                        appointment.setGoogleEventId(meeting.externalEventId());
                    });
        } catch (Exception ex) {
            log.error("Could not create a meeting for appointment {}; the appointment stands "
                    + "without a link.", appointment.getReference(), ex);
        }
    }
}
