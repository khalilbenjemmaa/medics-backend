package com.reemamiri.practice.admin.dto;

import com.reemamiri.practice.appointment.dto.AppointmentSummary;
import java.util.List;

/** Everything the dashboard's overview cards need, in one request. */
public record DashboardResponse(
        long todayCount,
        long upcomingCount,
        long onlineCount,
        long onSiteCount,
        AppointmentSummary nextAppointment,
        List<AppointmentSummary> today,
        List<AppointmentSummary> upcoming,
        String timezone,
        /**
         * Things that need a person to do something, rather than
         * counts to glance at. Turns the dashboard from a summary into
         * a worklist.
         */
        Attention attention) {

    public record Attention(
            /** Online appointments with no meeting link to send. */
            List<AppointmentSummary> missingMeetingLink,
            /** Contact messages nobody has opened. */
            long unreadMessages,
            /** Appointments in the past still marked confirmed. */
            List<AppointmentSummary> awaitingOutcome) {

        public boolean isEmpty() {
            return missingMeetingLink.isEmpty() && unreadMessages == 0 && awaitingOutcome.isEmpty();
        }
    }
}
