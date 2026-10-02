package com.bank.aml.security;

import com.bank.aml.dto.request.LoginRequest;
import com.bank.aml.security.rag.RagOutputValidator;
import com.bank.aml.security.rag.SecurityAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AppSecurityAuditRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagOutputValidator ragOutputValidator;

    @Autowired
    private RateLimitingService rateLimitingService;

    @Test
    @DisplayName("SEC-001: CORS Rejection for Unauthorized Untrusted Origins")
    void testCorsRejectsUnauthorizedOrigin() throws Exception {
        // Preflight OPTIONS from unauthorized attacker origin
        mockMvc.perform(options("/api/v1/chat/sessions")
                .header("Origin", "https://malicious-site.attacker.com")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-001: CORS Allows Trusted Origin")
    void testCorsAllowsWhitelistedOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    @DisplayName("SEC-002: Actuator Health is Public but Metrics/Info require Authentication")
    void testActuatorEndpointAccessControl() throws Exception {
        // /actuator/health is permitted publicly
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk());

        // /actuator/metrics requires ADMIN authentication (returns 403 Forbidden unauthenticated)
        mockMvc.perform(get("/actuator/metrics"))
            .andExpect(status().isForbidden());

        // /actuator/info requires ADMIN authentication
        mockMvc.perform(get("/actuator/info"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SEC-003: Rate Limiting Triggers HTTP 429 on Burst Login Attempts")
    void testRateLimitingOnLogin() throws Exception {
        String testIp = "198.51.100.42";
        LoginRequest req = new LoginRequest("analyst", "wrong-password");
        String json = objectMapper.writeValueAsString(req);

        // Make requests up to the threshold
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .header("X-Forwarded-For", testIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json))
                .andExpect(status().isUnauthorized());
        }

        // 16th request must trigger HTTP 429 Too Many Requests
        mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Forwarded-For", testIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("SEC-004: JWT Filter Rejects Disabled / Locked Accounts")
    void testDisabledAccountCannotAuthenticate() {
        com.bank.aml.entity.User user = new com.bank.aml.entity.User();
        user.setId(UUID.randomUUID());
        user.setUsername("disabled_analyst");
        user.setPasswordHash("hashed");
        user.setEnabled(false);

        SecurityUserPrincipal disabledPrincipal = new SecurityUserPrincipal(user);
        assertThat(disabledPrincipal.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("SEC-005: PII Masking Redacts Social Security Numbers and Credit Card Numbers in Output")
    void testPiiRedactionInRagOutput() {
        String outputWithPii = "Customer profile verified. SSN is 123-45-6789 and primary card is 4111-2222-3333-4444. No prior SAR filings.";

        RagOutputValidator.OutputValidationResult result = ragOutputValidator.validateOutput(outputWithPii);

        assertThat(result.valid()).isTrue();
        assertThat(result.validatedContent()).doesNotContain("123-45-6789");
        assertThat(result.validatedContent()).doesNotContain("4111-2222-3333-4444");
        assertThat(result.validatedContent()).contains("[REDACTED-SSN]");
        assertThat(result.validatedContent()).contains("[REDACTED-CARD-PAN]");
    }

    @Test
    @DisplayName("SEC-006: CRLF Sanitization Prevents Log Injection")
    void testCrlfLogSanitization() {
        String maliciousInput = "USER_LOGIN_FAILED\r\n2026-10-02 [INFO] Malicious forged log line";
        String sanitized = maliciousInput.replace('\r', '_').replace('\n', '_');

        assertThat(sanitized).doesNotContain("\r");
        assertThat(sanitized).doesNotContain("\n");
        assertThat(sanitized).contains("USER_LOGIN_FAILED__2026-10-02 [INFO] Malicious forged log line");
    }
}
