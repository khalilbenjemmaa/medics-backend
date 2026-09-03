package com.reemamiri.practice.notification;

import com.reemamiri.practice.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Wiring for outbound notifications.
 *
 * @EnableAsync is what makes the @Async on the email service take
 * effect. Without it the annotation is silently ignored and sending
 * happens inside the booking request — which is the failure mode that
 * looks fine until an SMTP server is slow.
 */
@Configuration
@EnableAsync
public class NotificationConfig {

    @Bean
    public EmailNotificationService.PracticeDetails practiceDetails(AppProperties properties) {
        AppProperties.Practice practice = properties.practice();
        return new EmailNotificationService.PracticeDetails(
                practice.name(),
                practice.practitionerName(),
                practice.practitionerRole(),
                practice.address(),
                practice.phone(),
                practice.phoneHref(),
                properties.notifications().email().replyTo());
    }
}
