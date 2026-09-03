package com.reemamiri.practice.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Every tunable the scheduling rules depend on, in one typed object.
 *
 * Binding these at startup means a bad timezone or a missing JWT secret
 * fails the application immediately, rather than throwing on the first
 * booking attempt at three in the morning.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull ZoneId doctorTimezone,
        @NotNull Booking booking,
        @NotNull Security security,
        @NotNull Cors cors,
        Notifications notifications) {

    public record Booking(
            @Min(5) int slotDurationMinutes,
            @Min(0) int minimumLeadTimeHours,
            @Min(1) int maximumHorizonDays) {}

    public record Security(@NotNull Jwt jwt, @NotNull Admin admin) {

        public record Jwt(
                @NotBlank String secret,
                @Min(1) int accessTokenMinutes,
                @NotBlank String issuer) {}

        /** Bootstrap credentials for the single operator account. */
        public record Admin(@NotBlank String email, String password) {}
    }

    public record Cors(List<String> allowedOrigins) {}

    public record Notifications(Email email) {
        public record Email(boolean enabled, String from) {}
    }
}
