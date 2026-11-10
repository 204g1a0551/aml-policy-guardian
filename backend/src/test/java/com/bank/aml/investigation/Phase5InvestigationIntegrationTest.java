package com.bank.aml.investigation;

import com.bank.aml.dto.request.AnalystDecisionRequest;
import com.bank.aml.dto.request.CreateAlertRequest;
import com.bank.aml.dto.request.ReviewerDecisionRequest;
import com.bank.aml.dto.request.UpdateInvestigationDraftRequest;
import com.bank.aml.entity.AmlAlert;
import com.bank.aml.entity.User;
import com.bank.aml.repository.AmlAlertRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.JwtTokenProvider;
import com.bank.aml.security.SecurityUserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class Phase5InvestigationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AmlAlertRepository alertRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

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
    @DisplayName("Alerts API: List alerts returns seeded synthetic alerts")
    void testListAlerts() throws Exception {
        mockMvc.perform(get("/api/v1/investigations/alerts")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").isArray())
            .andExpect(jsonPath("$.content.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("Alerts API: Generate synthetic alert persists new scenario and transactions")
    void testGenerateSyntheticAlert() throws Exception {
        CreateAlertRequest req = new CreateAlertRequest(
            "TM-RULE-101", "Structuring Test", "CUST-INT-99", "Synthetic Corp",
            "HIGH", "ACCT-INT-11", BigDecimal.valueOf(27000.00), "USD", "HIGH"
        );

        mockMvc.perform(post("/api/v1/investigations/alerts/generate")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertReference").isNotEmpty())
            .andExpect(jsonPath("$.scenarioCode").value("TM-RULE-101"))
            .andExpect(jsonPath("$.transactions.length()").value(3));
    }

    @Test
    @DisplayName("Investigation Workflow: Complete end-to-end investigation lifecycle with RBAC gates")
    void testCompleteInvestigationLifecycle() throws Exception {
        // 1. Pick a seeded alert
        AmlAlert alert = alertRepository.findAll().stream().findFirst().orElseThrow();
        UUID alertId = alert.getId();

        // 2. Trigger AI Analysis
        String analyzeResponse = mockMvc.perform(post("/api/v1/investigations/alerts/" + alertId + "/analyze")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT_READY"))
            .andExpect(jsonPath("$.sarNarrativeDraft").isNotEmpty())
            .andExpect(jsonPath("$.decisionRecommendation").isNotEmpty())
            .andExpect(jsonPath("$.linkedPolicies").isArray())
            .andReturn().getResponse().getContentAsString();

        JsonNode analyzeJson = objectMapper.readTree(analyzeResponse);
        String investigationId = analyzeJson.get("id").asText();

        // 3. Analyst edits draft
        UpdateInvestigationDraftRequest draftEdit = new UpdateInvestigationDraftRequest(
            "AMENDED DRAFT: Confirmed subject executed structuring transactions under 31 CFR § 1010.311.",
            "Manual review completed with branches."
        );

        mockMvc.perform(put("/api/v1/investigations/" + investigationId + "/draft")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(draftEdit)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.analystDecision").value("EDITED"))
            .andExpect(jsonPath("$.sarNarrativeDraft").value(org.hamcrest.Matchers.containsString("AMENDED DRAFT")));

        // 4. Analyst accepts and submits for review
        AnalystDecisionRequest decisionReq = new AnalystDecisionRequest(
            "ACCEPTED", null, "Draft finalized and ready for compliance officer sign-off."
        );

        mockMvc.perform(post("/api/v1/investigations/" + investigationId + "/analyst-decision")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(decisionReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
            .andExpect(jsonPath("$.analystDecision").value("ACCEPTED"));

        // 5. Reviewer decision: Verify non-admin (analyst) is rejected with 403 Forbidden
        ReviewerDecisionRequest reviewerReq = new ReviewerDecisionRequest(
            "APPROVED", "Concur with SAR recommendation. Approved for FinCEN filing.", null
        );

        mockMvc.perform(post("/api/v1/investigations/" + investigationId + "/reviewer-decision")
                .header("Authorization", "Bearer " + analystToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reviewerReq)))
            .andExpect(status().isForbidden());

        // 6. Reviewer decision: Admin approves
        mockMvc.perform(post("/api/v1/investigations/" + investigationId + "/reviewer-decision")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reviewerReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.reviewerDecision").value("APPROVED"))
            .andExpect(jsonPath("$.completedAt").isNotEmpty());

        // 7. Export dossier report
        mockMvc.perform(get("/api/v1/investigations/" + investigationId + "/export")
                .header("Authorization", "Bearer " + analystToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reportFormat").value("MARKDOWN_DOSSIER"))
            .andExpect(jsonPath("$.markdownContent").value(org.hamcrest.Matchers.containsString("# AML INVESTIGATION COMPLIANCE REPORT")))
            .andExpect(jsonPath("$.legalDisclaimer").value(org.hamcrest.Matchers.containsString("does NOT constitute an automated SAR filing decision")));
    }
}
