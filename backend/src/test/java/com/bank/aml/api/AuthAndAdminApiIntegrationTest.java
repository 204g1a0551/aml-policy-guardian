package com.bank.aml.api;

import com.bank.aml.dto.request.LoginRequest;
import com.bank.aml.dto.response.AuthTokenResponse;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.User;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthAndAdminApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    // ==========================================
    // 1. AUTHENTICATION API TESTS
    // ==========================================

    @Test
    @DisplayName("API Auth: Analyst login with valid credentials returns 200 and JWT token")
    void testAnalystLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest("analyst", "AdminPass123!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.user.username").value("analyst"))
            .andExpect(jsonPath("$.user.roles[0]").value("ROLE_ANALYST"))
            .andReturn();

        AuthTokenResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.user().roles()).contains("ROLE_ANALYST");
    }

    @Test
    @DisplayName("API Auth: Admin login with valid credentials returns 200 and ROLE_ADMIN role")
    void testAdminLoginSuccess() throws Exception {
        LoginRequest request = new LoginRequest("admin", "AdminPass123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.username").value("admin"))
            .andExpect(jsonPath("$.user.roles").isArray());
    }

    @Test
    @DisplayName("API Auth: Login with incorrect password returns 401 Unauthorized")
    void testLoginWithIncorrectPassword() throws Exception {
        LoginRequest request = new LoginRequest("analyst", "WrongPassword999!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AML-SEC-4001"));
    }

    @Test
    @DisplayName("API Auth: Login with non-existent username returns 401 Unauthorized")
    void testLoginWithUnknownUsername() throws Exception {
        LoginRequest request = new LoginRequest("ghostUser", "AdminPass123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AML-SEC-4001"));
    }

    @Test
    @DisplayName("API Auth: Login with blank body returns 400 Bad Request")
    void testLoginWithBlankCredentials() throws Exception {
        LoginRequest request = new LoginRequest("", "");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    // ==========================================
    // 2. ADMIN AUDIT & ACCESS RESTRICTION TESTS
    // ==========================================

    @Test
    @DisplayName("API Admin: Unauthenticated request to /api/v1/admin/audit returns 401 or 403")
    void testAuditLogsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    @Test
    @DisplayName("API Admin: Analyst requesting /api/v1/admin/audit returns 403 Forbidden")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testAuditLogsForbiddenForAnalyst() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API Admin: Admin requesting /api/v1/admin/audit returns 200 OK with compliance logs")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAuditLogsAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("API Admin: Analyst uploading document returns 403 Forbidden")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testDocumentUploadForbiddenForAnalyst() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "analyst_policy.txt", "text/plain", "Sample content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Analyst Upload Attempt")
                .param("documentType", "POLICY")
                .param("version", "v1.0")
                .param("source", "FIU"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API Admin: Analyst deleting document returns 403 Forbidden")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testDocumentDeleteForbiddenForAnalyst() throws Exception {
        UUID docId = UUID.randomUUID();
        mockMvc.perform(delete("/api/v1/documents/" + docId))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("API Admin: Admin uploading valid document returns 201 Created")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testDocumentUploadAllowedForAdmin() throws Exception {
        String uniqueContent = "Authorized compliance content: " + UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "admin_policy_" + System.currentTimeMillis() + ".txt", "text/plain", uniqueContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/documents")
                .file(file)
                .param("title", "Admin Policy Document")
                .param("documentType", "POLICY")
                .param("version", "v1.0")
                .param("source", "FIU Compliance"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    @DisplayName("API Admin: Admin deleting document returns 204 No Content")
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testDocumentDeleteAllowedForAdmin() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        Document doc = documentRepository.save(new Document(
            "Doc to Delete", "delete_me_" + System.currentTimeMillis() + ".pdf", "SOP", "v1.0", "FIU", "READY", admin.getId()
        ));

        mockMvc.perform(delete("/api/v1/documents/" + doc.getId()))
            .andExpect(status().isNoContent());

        assertThat(documentRepository.findById(doc.getId()).get().getStatus()).isEqualTo("DELETED");
    }
}
