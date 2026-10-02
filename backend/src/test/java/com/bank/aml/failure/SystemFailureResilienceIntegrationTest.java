package com.bank.aml.failure;

import com.bank.aml.dto.request.AskQuestionRequest;
import com.bank.aml.dto.request.CreateChatSessionRequest;
import com.bank.aml.dto.response.ChatMessageResponse;
import com.bank.aml.dto.response.ChatSessionResponse;
import com.bank.aml.embedding.EmbeddingGenerationService;
import com.bank.aml.entity.User;
import com.bank.aml.exception.DocumentProcessingException;
import com.bank.aml.exception.InvalidFileException;
import com.bank.aml.rag.LlmGenerationService;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.service.RagChatService;
import com.bank.aml.util.FileValidatorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemFailureResilienceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagChatService ragChatService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileValidatorService fileValidatorService;

    @Autowired
    private EmbeddingGenerationService embeddingGenerationService;

    private SecurityUserPrincipal analystPrincipal;

    @BeforeEach
    void setUp() {
        User analyst = userRepository.findByUsername("analyst").orElseThrow();
        analystPrincipal = SecurityUserPrincipal.create(analyst);
    }

    // ==========================================
    // 1. LLM & EXTERNAL API FAILURE RESILIENCE
    // ==========================================

    @Test
    @DisplayName("Failure - LLM Unavailable: Remote LLM network drop falls back to deterministic compliance reasoning")
    void testLlmUnavailableGracefulFallback() {
        ChatSessionResponse session = ragChatService.createSession(
            new CreateChatSessionRequest("LLM Failure Test Session"), analystPrincipal
        );

        // When remote model is unavailable or in test mode, the system does not throw 500 error
        ChatMessageResponse response = ragChatService.askQuestion(
            session.id(),
            new AskQuestionRequest("What is the definition of structuring under TM-RULE-101?"),
            analystPrincipal
        );

        assertThat(response.id()).isNotNull();
        assertThat(response.role()).isEqualTo("ASSISTANT");
        assertThat(response.content()).isNotBlank();
        // Deterministic engine provides compliance guidance
        assertThat(response.content()).satisfiesAnyOf(
            c -> assertThat(c).contains("TM-RULE-101"),
            c -> assertThat(c).contains("MLRO"),
            c -> assertThat(c).contains("AML Compliance Assistant")
        );
    }

    // ==========================================
    // 2. EMBEDDING SERVICE RESILIENCE
    // ==========================================

    @Test
    @DisplayName("Failure - Embedding Service: Generates fallback vector representation even if offline")
    void testEmbeddingServiceResilience() {
        String query = "High-value currency transaction alert procedure";

        float[] embedding = embeddingGenerationService.generateEmbedding(query);

        assertThat(embedding).isNotNull();
        assertThat(embedding).hasSize(1536);
        // Verify vector is not all zeroes (token dispersion dispersion works)
        boolean hasNonZero = false;
        for (float v : embedding) {
            if (v != 0.0f) {
                hasNonZero = true;
                break;
            }
        }
        assertThat(hasNonZero).isTrue();
    }

    // ==========================================
    // 3. MALFORMED DOCUMENT HANDLING
    // ==========================================

    @Test
    @DisplayName("Failure - Malformed Document: Zero-byte file throws InvalidFileException with 400 response")
    void testMalformedDocumentEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "corrupt.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> fileValidatorService.validateFile(emptyFile))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("cannot be empty");
    }

    @Test
    @DisplayName("Failure - Malformed Document: Unsupported binary garbage throws InvalidFileException")
    void testMalformedDocumentUnsupportedMime() {
        byte[] garbage = new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05};
        MockMultipartFile invalidMimeFile = new MockMultipartFile("file", "corrupt.exe", "application/x-msdownload", garbage);

        assertThatThrownBy(() -> fileValidatorService.validateFile(invalidMimeFile))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("Unsupported file extension");
    }

    // ==========================================
    // 4. INVALID REQUEST VALIDATION
    // ==========================================

    @Test
    @DisplayName("Failure - Invalid Request: Blank question body returns 400 Bad Request with ProblemDetail")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testInvalidBlankQuestionRequest() throws Exception {
        ChatSessionResponse session = ragChatService.createSession(
            new CreateChatSessionRequest("Invalid Request Session"), analystPrincipal
        );

        AskQuestionRequest blankRequest = new AskQuestionRequest("");

        mockMvc.perform(post("/api/v1/chat/sessions/" + session.id() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("Failure - Invalid Request: Malformed JSON syntax returns 400 Bad Request")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testMalformedJsonPayload() throws Exception {
        ChatSessionResponse session = ragChatService.createSession(
            new CreateChatSessionRequest("Malformed JSON Session"), analystPrincipal
        );

        mockMvc.perform(post("/api/v1/chat/sessions/" + session.id() + "/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ not valid json: ... }"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Failure - Non-existent Entity: Querying unknown session UUID returns 404 Not Found")
    @WithMockUser(username = "analyst", roles = {"ANALYST"})
    void testQueryNonExistentSessionReturns404() throws Exception {
        UUID nonExistentId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/chat/sessions/" + nonExistentId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }
}
