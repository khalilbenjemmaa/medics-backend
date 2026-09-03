package com.reemamiri.practice.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reemamiri.practice.AbstractIntegrationTest;
import com.reemamiri.practice.security.dto.LoginRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The security boundary: what the public may reach, and what it may not.
 */
@AutoConfigureMockMvc
class SecurityTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AdminUserRepository adminUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    /**
     * Creates the operator account for each test.
     *
     * The production bootstrap is an ApplicationRunner, which fires once
     * at startup — but the database is refreshed between test methods,
     * so a row created then would not survive. Seeding here also keeps
     * the login tests exercising the real hash-and-compare path rather
     * than a fixture.
     */
    @BeforeEach
    void createAdminAccount() {
        if (adminUserRepository.existsByEmailIgnoreCase("admin@test.local")) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setEmail("admin@test.local");
        admin.setPasswordHash(passwordEncoder.encode("test-admin-password"));
        admin.setDisplayName("Test administrator");
        adminUserRepository.save(admin);
    }

    /* ---------------- public surface stays public ---------------- */

    @Test
    @DisplayName("availability is readable without authentication")
    void availabilityIsPublic() throws Exception {
        LocalDate from = LocalDate.now().plusDays(1);
        mockMvc.perform(get("/api/v1/availability")
                        .param("from", from.toString())
                        .param("to", from.plusDays(7).toString()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("concern categories are readable without authentication")
    void categoriesArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/concern-categories")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the contact form accepts submissions without authentication")
    void contactIsPublic() throws Exception {
        String body = """
                {"firstName":"Sam","lastName":"Reed","email":"sam@example.test",
                 "phone":"+33600000000","message":"Do you have evening appointments?"}
                """;
        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
    }

    /* ---------------- admin surface is closed ---------------- */

    @Test
    @DisplayName("every admin endpoint refuses an unauthenticated request")
    void adminEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/appointments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/patients")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/availability/weekly")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/blocked-periods")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/calendar")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-01-02T00:00:00Z")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/admin/appointments/" + UUID.randomUUID() + "/cancel"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a patient record cannot be read without authentication")
    void patientDataIsNotPublic() throws Exception {
        mockMvc.perform(get("/api/v1/admin/patients/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a forged or malformed token is refused")
    void forgedTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer not.a.real.token"))
                .andExpect(status().isUnauthorized());
    }

    /* ---------------- login ---------------- */

    @Test
    @DisplayName("correct credentials return a token")
    void loginSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@test.local", "test-admin-password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("a wrong password is refused, and reveals nothing about the account")
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@test.local", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                // The same message an unknown address gets: differing
                // replies would confirm which addresses exist.
                .andExpect(jsonPath("$.message").value("Authentication failed."));
    }

    @Test
    @DisplayName("an unknown account is refused identically")
    void unknownAccountIsRejectedIdentically() throws Exception {
        mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody@test.local", "test-admin-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication failed."));
    }

    @Test
    @DisplayName("a valid token opens the admin area")
    void validTokenGrantsAccess() throws Exception {
        String response = mockMvc.perform(post("/api/v1/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("admin@test.local", "test-admin-password"))))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("accessToken").asText();

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("validation failures report the offending field and no internals")
    void validationErrorsAreShaped() throws Exception {
        String body = """
                {"firstName":"","lastName":"Reed","email":"not-an-email",
                 "message":"hello"}
                """;
        mockMvc.perform(post("/api/v1/contact")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").isNotEmpty());
    }
}
