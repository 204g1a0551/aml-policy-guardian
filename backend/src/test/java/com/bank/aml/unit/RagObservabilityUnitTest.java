package com.bank.aml.unit;

import com.bank.aml.observability.RagObservabilityService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RagObservabilityUnitTest {

    private SimpleMeterRegistry meterRegistry;
    private RagObservabilityService observabilityService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        observabilityService = new RagObservabilityService(meterRegistry, null);
    }

    @Test
    @DisplayName("Observability: Record retrieval, reranking, and generation latencies")
    void testLatencyRecording() {
        observabilityService.recordRetrieval(Duration.ofMillis(45), 5, "HYBRID");
        observabilityService.recordReranking(Duration.ofMillis(15), "HYBRID_RERANK");
        observabilityService.recordGeneration(Duration.ofMillis(120), "gemini-1.5-flash");
        observabilityService.recordTimeToFirstToken(Duration.ofMillis(30));
        observabilityService.recordTotalRequest(Duration.ofMillis(180), false, false);

        RagObservabilityService.ObservabilitySnapshot snapshot = observabilityService.getSnapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(1);
        assertThat(snapshot.successfulResponses()).isEqualTo(1);
        assertThat(snapshot.refusals()).isEqualTo(0);
        assertThat(snapshot.modelFailures()).isEqualTo(0);
        assertThat(snapshot.successRatePercentage()).isEqualTo(100.0);

        assertThat(snapshot.p50RetrievalLatencyMs()).isGreaterThan(0.0);
        assertThat(snapshot.p50GenerationLatencyMs()).isGreaterThan(0.0);
        assertThat(snapshot.p50TotalLatencyMs()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Observability: Token consumption and cost calculation")
    void testTokenUsageAndCostCalculation() {
        observabilityService.recordTokenUsage(1000, 500, "gemini-1.5-flash");

        RagObservabilityService.ObservabilitySnapshot snapshot = observabilityService.getSnapshot();

        assertThat(snapshot.totalPromptTokens()).isEqualTo(1000);
        assertThat(snapshot.totalCompletionTokens()).isEqualTo(500);

        // Expected cost: 1000 * 0.00015/1000 ($0.00015) + 500 * 0.00060/1000 ($0.00030) = $0.00045
        assertThat(snapshot.totalEstimatedCostUsd()).isGreaterThan(0.0004);
        assertThat(snapshot.totalEstimatedCostUsd()).isLessThan(0.0005);
    }

    @Test
    @DisplayName("Observability: Calibrated evidence quality distribution tracking")
    void testEvidenceQualityDistribution() {
        observabilityService.recordEvidenceQuality("HIGH", 0.85);
        observabilityService.recordEvidenceQuality("HIGH", 0.90);
        observabilityService.recordEvidenceQuality("MEDIUM", 0.65);
        observabilityService.recordEvidenceQuality("LOW", 0.40);
        observabilityService.recordEvidenceQuality("INSUFFICIENT", 0.15);

        RagObservabilityService.ObservabilitySnapshot snapshot = observabilityService.getSnapshot();

        assertThat(snapshot.highQualityCount()).isEqualTo(2);
        assertThat(snapshot.mediumQualityCount()).isEqualTo(1);
        assertThat(snapshot.lowQualityCount()).isEqualTo(1);
        assertThat(snapshot.insufficientQualityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Observability: Refusals and model failures tracking")
    void testRefusalsAndFailures() {
        observabilityService.recordTotalRequest(Duration.ofMillis(50), true, false); // refusal
        observabilityService.recordTotalRequest(Duration.ofMillis(30), false, true); // failure
        observabilityService.recordModelFailure("LLM_TIMEOUT");

        RagObservabilityService.ObservabilitySnapshot snapshot = observabilityService.getSnapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(2);
        assertThat(snapshot.refusals()).isEqualTo(1);
        assertThat(snapshot.modelFailures()).isEqualTo(2); // 1 from totalRequest + 1 from recordModelFailure
        assertThat(snapshot.refusalRatePercentage()).isEqualTo(50.0);
    }
}
