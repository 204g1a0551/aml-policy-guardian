package com.bank.aml.api;

import com.bank.aml.dto.request.AskQuestionRequest;
import com.bank.aml.dto.request.CreateChatSessionRequest;
import com.bank.aml.dto.request.StreamQuestionRequest;
import com.bank.aml.dto.response.ChatMessageResponse;
import com.bank.aml.dto.response.ChatSessionResponse;
import com.bank.aml.entity.ChatSession;
import com.bank.aml.entity.Document;
import com.bank.aml.entity.User;
import com.bank.aml.repository.ChatSessionRepository;
import com.bank.aml.repository.DocumentRepository;
import com.bank.aml.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentAndChatApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    // ==========================================
    // 1. DOCUMENT APIS
    // ==========================================

    @Test
    @DisplayName("API Document: Analyst and Admin can retrieve list of approved documents")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testGetDocumentsList() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("API Document: Retrieve document by existing ID returns 200 OK")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testGetDocumentByIdSuccess() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();
        Document doc = documentRepository.save(new Document(
            "Test Policy", "test_policy_" + System.currentTimeMillis() + ".pdf", "POLICY", "v1.0", "FIU", "ACTIVE", admin.getId()
        ));

        mockMvc.perform(get("/api/v1/documents/" + doc.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(doc.getId().toString()))
            .andExpect(jsonPath("$.title").value("Test Policy"));
    }

    @Test
    @DisplayName("API Document: Retrieve non-existent document ID returns 404 Not Found")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testGetDocumentByIdNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/documents/" + UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ==========================================
    // 2. CHAT & HISTORY APIS
    // ==========================================

    @Test
    @DisplayName("API Chat: Analyst creates session and retrieves personal sessions list")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testCreateAndListChatSessions() throws Exception {
        CreateChatSessionRequest request = new CreateChatSessionRequest("SAR Filing Investigation");

        MvcResult createResult = mockMvc.perform(post("/api/v1/chat/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.title").value("SAR Filing Investigation"))
            .andReturn();

        ChatSessionResponse session = objectMapper.readValue(createResult.getResponse().getContentAsString(), ChatSessionResponse.class);

        mockMvc.perform(get("/api/v1/chat/sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$[?(@.id == '" + session.id() + "')]").exists());
    }

    @Test
    @DisplayName("API Chat: Synchronous inquiry generates assistant answer with citations")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testAskQuestionSynchronous() throws Exception {
        // Create session
        User analyst = userRepository.findByUsername("analyst").orElseThrow();
        ChatSession session = chatSessionRepository.save(new ChatSession(analyst.getId(), "Inquiry Session"));

        AskQuestionRequest request = new AskQuestionRequest("What is the threshold for CTR reporting?");

        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions/" + session.getId() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNotEmpty())
            .andExpect(jsonPath("$.role").value("ASSISTANT"))
            .andExpect(jsonPath("$.content").isNotEmpty())
            .andExpect(jsonPath("$.citations").isArray())
            .andReturn();

        ChatMessageResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), ChatMessageResponse.class);
        assertThat(response.content()).isNotBlank();
    }

    @Test
    @DisplayName("API Chat: Session transcript maintains chronological message history")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testGetSessionTranscriptHistory() throws Exception {
        User analyst = userRepository.findByUsername("analyst").orElseThrow();
        ChatSession session = chatSessionRepository.save(new ChatSession(analyst.getId(), "History Session"));

        // Ask question
        mockMvc.perform(post("/api/v1/chat/sessions/" + session.getId() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AskQuestionRequest("Inquiry 1"))))
            .andExpect(status().isCreated());

        // Get session transcript
        mockMvc.perform(get("/api/v1/chat/sessions/" + session.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(session.getId().toString()))
            .andExpect(jsonPath("$.messages.length()").value(2))
            .andExpect(jsonPath("$.messages[0].role").value("USER"))
            .andExpect(jsonPath("$.messages[1].role").value("ASSISTANT"));
    }

    @Test
    @DisplayName("API Chat IDOR: Analyst cannot access or submit inquiries to another user's session")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testCrossUserSessionAccessForbidden() throws Exception {
        // Create session belonging to admin
        User admin = userRepository.findByUsername("admin").orElseThrow();
        ChatSession adminSession = chatSessionRepository.save(new ChatSession(admin.getId(), "Admin Confidential Session"));

        // Analyst tries to access admin's session -> 403 Forbidden
        mockMvc.perform(get("/api/v1/chat/sessions/" + adminSession.getId()))
            .andExpect(status().isForbidden());

        // Analyst tries to send message to admin's session -> 403 Forbidden
        mockMvc.perform(post("/api/v1/chat/sessions/" + adminSession.getId() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AskQuestionRequest("Can I read this?"))))
            .andExpect(status().isForbidden());
    }
}
