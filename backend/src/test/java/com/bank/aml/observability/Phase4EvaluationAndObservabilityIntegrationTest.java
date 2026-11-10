package com.bank.aml.observability;

import com.bank.aml.dto.request.AskQuestionRequest;
import com.bank.aml.dto.request.CreateChatSessionRequest;
import com.bank.aml.dto.response.ChatSessionResponse;
import com.bank.aml.entity.User;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.JwtTokenProvider;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.service.RagChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase4EvaluationAndObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private RagChatService ragChatService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagObservabilityService observabilityService;

    private User adminUser;
    private User analystUser;

    private String adminToken;
    private String analystToken;

    private SecurityUserPrincipal adminPrincipal;
    private SecurityUserPrincipal analystPrincipal;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.findByUsername("admin").orElseThrow();
        analystUser = userRepository.findByUsername("analyst").orElseThrow();

        adminPrincipal = SecurityUserPrincipal.create(adminUser);
        analystPrincipal = SecurityUserPrincipal.create(analystUser);

        adminToken = jwtTokenProvider.generateToken(adminPrincipal);
        analystToken = jwtTokenProvider.generateToken(analystPrincipal);
    }

    @Test
    @DisplayName("Prometheus Endpoint: Actuator exposes prometheus formatted metrics")
    void testPrometheusMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(result -> {
                String content = result.getResponse().getContentAsString();
                assertThat(content).contains("jvm_memory");
            });
    }

    @Test
    @DisplayName("Evaluation API: Returns real-time latency percentiles, token usage, and system health")
    void testEvaluationMetricsApi() throws Exception {
        // Execute a question to ensure metrics exist
        ChatSessionResponse session = ragChatService.createSession(new CreateChatSessionRequest("Obs Session"), analystPrincipal);
        ragChatService.askQuestion(session.id(), new AskQuestionRequest("What is the mandatory threshold for filing a Currency Transaction Report?"), analystPrincipal);

        mockMvc.perform(get("/api/v1/admin/evaluation/metrics")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default-bank"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.systemHealth").exists())
            .andExpect(jsonPath("$.performanceSnapshot.totalRequests").isNumber())
            .andExpect(jsonPath("$.performanceSnapshot.p50TotalLatencyMs").isNumber())
            .andExpect(jsonPath("$.performanceSnapshot.p95TotalLatencyMs").isNumber())
            .andExpect(jsonPath("$.performanceSnapshot.totalEstimatedCostUsd").isNumber());
    }

    @Test
    @DisplayName("Evaluation API: Returns historical benchmark run results")
    void testEvaluationHistoryApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evaluation/history")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default-bank"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[0].passRatePercentage").isNumber())
            .andExpect(jsonPath("$[0].groundednessRate").isNumber());
    }

    @Test
    @DisplayName("Evaluation API: Returns active regression alerts")
    void testEvaluationAlertsApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evaluation/alerts")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default-bank"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Evaluation API: Compares two benchmark runs and calculates delta")
    void testEvaluationCompareApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/evaluation/compare")
                .param("baselineId", "baseline_run_phase3")
                .param("candidateId", "baseline_run_phase3")
                .header("Authorization", "Bearer " + adminToken)
                .header("X-Tenant-ID", "default-bank"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summaryVerdict").exists())
            .andExpect(jsonPath("$.isRegression").value(false));
    }

    @Test
    @DisplayName("Tracing & Correlation: Correlation ID is returned in response header and propagated")
    void testCorrelationIdHeaderPropagation() throws Exception {
        String testCorrId = "corr-test-trace-" + java.util.UUID.randomUUID();

        mockMvc.perform(get("/actuator/health")
                .header("X-Correlation-ID", testCorrId))
            .andExpect(status().isOk())
            .andExpect(result -> {
                String responseHeader = result.getResponse().getHeader("X-Correlation-ID");
                assertThat(responseHeader).isEqualTo(testCorrId);
            });
    }

    @Test
    @DisplayName("RBAC: Unauthenticated and non-admin requests to evaluation metrics are rejected")
    void testRbacOnEvaluationMetrics() throws Exception {
        // Unauthenticated -> 401 Unauthorized
        mockMvc.perform(get("/api/v1/admin/evaluation/metrics"))
            .andExpect(status().isUnauthorized());

        // Regular Analyst without AUDITOR or ADMIN role -> 403 Forbidden
        mockMvc.perform(get("/api/v1/admin/evaluation/metrics")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isForbidden());
    }
}
