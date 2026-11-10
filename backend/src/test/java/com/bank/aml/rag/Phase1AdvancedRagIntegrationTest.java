package com.bank.aml.rag;

import com.bank.aml.dto.request.AskQuestionRequest;
import com.bank.aml.dto.request.CreateChatSessionRequest;
import com.bank.aml.dto.request.RetrievalFilterDto;
import com.bank.aml.dto.response.ChatMessageResponse;
import com.bank.aml.dto.response.ChatSessionResponse;
import com.bank.aml.dto.response.CitationResponse;
import com.bank.aml.dto.response.RetrievalComparisonResponse;
import com.bank.aml.entity.User;
import com.bank.aml.repository.ChunkSimilarityProjection;
import com.bank.aml.repository.DocumentChunkRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.security.rag.AuthorizedRetrievalService;
import com.bank.aml.service.RagChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class Phase1AdvancedRagIntegrationTest {

    @Autowired
    private RagChatService ragChatService;

    @Autowired
    private AuthorizedRetrievalService authorizedRetrievalService;

    @Autowired
    private RetrievalComparisonService retrievalComparisonService;

    @Autowired
    private DocumentChunkRepository documentChunkRepository;

    @Autowired
    private UserRepository userRepository;

    private SecurityUserPrincipal analystPrincipal;

    @BeforeEach
    void setUp() {
        User analyst = userRepository.findByUsername("analyst").orElseThrow();
        analystPrincipal = SecurityUserPrincipal.create(analyst);
    }

    @Test
    @DisplayName("Phase 1: PostgreSQL full-text search retrieves policy chunks via generated tsvector")
    void testPostgresFullTextSearchOnTsvector() {
        List<ChunkSimilarityProjection> chunks = documentChunkRepository.findChunksByKeywordSearch(
            "Currency Transaction Report",
            null, null, null, 5
        );

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getChunkText()).containsIgnoringCase("Currency Transaction");
        assertThat(chunks.get(0).getSimilarity()).isNotNull();
    }

    @Test
    @DisplayName("Phase 1: Filtered vector search respects documentType and section metadata filters")
    void testFilteredRetrieval() {
        RetrievalFilterDto filters = new RetrievalFilterDto(
            null,
            "PROCEDURE",
            "Section 2",
            "HYBRID_RERANK"
        );

        List<ScoredChunk> chunks = authorizedRetrievalService.retrieveAuthorizedContext(
            "What steps should I follow for high-value transaction alerts?",
            analystPrincipal,
            filters
        );

        assertThat(chunks).isNotEmpty();
        for (ScoredChunk chunk : chunks) {
            assertThat(chunk.getDocumentType()).isEqualTo("PROCEDURE");
            if (chunk.getSection() != null) {
                assertThat(chunk.getSection()).containsIgnoringCase("Section 2");
            }
        }
    }

    @Test
    @DisplayName("Phase 1: RRF and Reranking elevate exact compliance threshold chunk with verified citations")
    void testHybridRerankWithVerifiedCitations() {
        ChatSessionResponse session = ragChatService.createSession(
            new CreateChatSessionRequest("Phase 1 Test Session"),
            analystPrincipal
        );

        ChatMessageResponse response = ragChatService.askQuestion(
            session.id(),
            new AskQuestionRequest("What is the mandatory threshold and filing deadline for a Currency Transaction Report?"),
            analystPrincipal
        );

        assertThat(response.content()).isNotEmpty();
        assertThat(response.citations()).isNotEmpty();

        CitationResponse topCitation = response.citations().get(0);
        assertThat(topCitation.documentTitle()).isNotEmpty();
        assertThat(topCitation.snippet()).isNotNull();
        assertThat(topCitation.verified()).isTrue();
        assertThat(topCitation.citationSupportScore()).isNotNull();

        // Calibrated evidence quality
        assertThat(response.evidenceQualityScore()).isNotNull();
        assertThat(response.evidenceLevel()).isIn("HIGH", "MEDIUM");
        assertThat(response.isRefusal()).isFalse();
    }

    @Test
    @DisplayName("Phase 1: Explicit insufficient-evidence response policy triggers graceful abstention")
    void testInsufficientEvidenceGracefulAbstention() {
        ChatSessionResponse session = ragChatService.createSession(
            new CreateChatSessionRequest("Abstention Session"),
            analystPrincipal
        );

        ChatMessageResponse response = ragChatService.askQuestion(
            session.id(),
            new AskQuestionRequest("What is the employee dress code and cafeteria menu on Fridays?"),
            analystPrincipal
        );

        assertThat(response.content()).contains("I cannot find sufficient guidance in the approved AML compliance policies to answer this inquiry.");
        assertThat(response.isRefusal()).isTrue();
        assertThat(response.evidenceLevel()).isEqualTo("INSUFFICIENT");
    }

    @Test
    @DisplayName("Phase 1: Retrieval strategy comparison executes Vector, Keyword, Hybrid and Rerank")
    void testRetrievalStrategyComparison() {
        String query = "What steps should I follow when a customer triggers a high-value transaction alert?";

        RetrievalComparisonResponse comparison = retrievalComparisonService.compareStrategies(
            query, analystPrincipal, 5
        );

        assertThat(comparison.query()).isEqualTo(query);
        assertThat(comparison.strategies()).containsKeys("VECTOR_ONLY", "KEYWORD_ONLY", "HYBRID", "HYBRID_RERANK");

        var vectorResult = comparison.strategies().get("VECTOR_ONLY");
        var hybridResult = comparison.strategies().get("HYBRID");
        var rerankResult = comparison.strategies().get("HYBRID_RERANK");

        assertThat(vectorResult.chunks()).isNotEmpty();
        assertThat(hybridResult.chunks()).isNotEmpty();
        assertThat(rerankResult.chunks()).isNotEmpty();

        assertThat(comparison.strategyJaccardOverlap()).isNotEmpty();
        assertThat(comparison.recommendation()).contains("HYBRID_RERANK");
    }
}
