package com.bank.aml.observability;

import com.bank.aml.security.correlation.CorrelationIdContext;
import io.micrometer.core.instrument.*;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * Enterprise RAG Observability and AI Performance Metrics Service.
 * Collects Micrometer metrics, OpenTelemetry distributed traces,
 * token consumption, estimated inference cost, and calibrated evidence quality.
 */
@Service
public class RagObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(RagObservabilityService.class);

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    // Timers
    private final Timer retrievalTimer;
    private final Timer rerankingTimer;
    private final Timer generationTimer;
    private final Timer timeToFirstTokenTimer;
    private final Timer totalRequestTimer;

    // Distribution Summaries
    private final DistributionSummary promptTokensSummary;
    private final DistributionSummary completionTokensSummary;
    private final DistributionSummary chunksRetrievedSummary;

    // Cumulative stats for instant dashboard queries
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong successfulResponses = new AtomicLong(0);
    private final AtomicLong refusals = new AtomicLong(0);
    private final AtomicLong modelFailures = new AtomicLong(0);
    private final DoubleAdder totalEstimatedCostUsd = new DoubleAdder();
    private final AtomicLong totalPromptTokens = new AtomicLong(0);
    private final AtomicLong totalCompletionTokens = new AtomicLong(0);

    private final ConcurrentHashMap<String, AtomicLong> evidenceQualityCounts = new ConcurrentHashMap<>();

    // Standard Gemini 1.5 Flash / GPT-4o-mini tier pricing benchmarks (per 1K tokens)
    private static final double INPUT_TOKEN_COST_PER_1K = 0.00015;
    private static final double OUTPUT_TOKEN_COST_PER_1K = 0.00060;

    @Autowired
    public RagObservabilityService(MeterRegistry meterRegistry, @Autowired(required = false) Tracer tracer) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        this.tracer = tracer;

        this.retrievalTimer = Timer.builder("rag.retrieval.latency")
            .description("Latency of pgvector semantic and keyword candidate retrieval")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(this.meterRegistry);

        this.rerankingTimer = Timer.builder("rag.reranking.latency")
            .description("Latency of Reciprocal Rank Fusion and cross-encoder reranking")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(this.meterRegistry);

        this.generationTimer = Timer.builder("rag.generation.latency")
            .description("Latency of total LLM answer generation")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(this.meterRegistry);

        this.timeToFirstTokenTimer = Timer.builder("rag.time_to_first_token")
            .description("Latency until first SSE token chunk is emitted")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(this.meterRegistry);

        this.totalRequestTimer = Timer.builder("rag.request.total_latency")
            .description("End-to-end request turnaround latency")
            .publishPercentiles(0.5, 0.95, 0.99)
            .publishPercentileHistogram()
            .register(this.meterRegistry);

        this.promptTokensSummary = DistributionSummary.builder("rag.tokens.prompt")
            .description("Number of input tokens per prompt")
            .register(this.meterRegistry);

        this.completionTokensSummary = DistributionSummary.builder("rag.tokens.completion")
            .description("Number of output tokens generated")
            .register(this.meterRegistry);

        this.chunksRetrievedSummary = DistributionSummary.builder("rag.chunks.retrieved")
            .description("Number of chunks retrieved for context")
            .register(this.meterRegistry);

        evidenceQualityCounts.put("HIGH", new AtomicLong(0));
        evidenceQualityCounts.put("MEDIUM", new AtomicLong(0));
        evidenceQualityCounts.put("LOW", new AtomicLong(0));
        evidenceQualityCounts.put("INSUFFICIENT", new AtomicLong(0));
    }

    // --- Trace Lifecycle Methods ---

    public AutoCloseableSpan startSpan(String spanName, String strategy) {
        if (tracer == null) {
            return new AutoCloseableSpan(null, null);
        }
        String correlationId = CorrelationIdContext.getCorrelationId();
        Span span = tracer.nextSpan()
            .name(spanName)
            .tag("correlation.id", correlationId)
            .tag("rag.strategy", strategy != null ? strategy : "DEFAULT");
        Tracer.SpanInScope ws = tracer.withSpan(span.start());
        return new AutoCloseableSpan(span, ws);
    }

    public static class AutoCloseableSpan implements AutoCloseable {
        private final Span span;
        private final Tracer.SpanInScope scope;

        public AutoCloseableSpan(Span span, Tracer.SpanInScope scope) {
            this.span = span;
            this.scope = scope;
        }

        public void tag(String key, String value) {
            if (span != null && value != null) {
                span.tag(key, value);
            }
        }

        public void error(Throwable throwable) {
            if (span != null && throwable != null) {
                span.error(throwable);
            }
        }

        @Override
        public void close() {
            if (scope != null) {
                scope.close();
            }
            if (span != null) {
                span.end();
            }
        }
    }

    // --- Metric Recording Methods ---

    public void recordRetrieval(Duration duration, int chunksCount, String strategy) {
        retrievalTimer.record(duration);
        chunksRetrievedSummary.record(chunksCount);
        Counter.builder("rag.retrieval.events")
            .tag("strategy", strategy != null ? strategy : "DEFAULT")
            .register(meterRegistry)
            .increment();
    }

    public void recordReranking(Duration duration, String strategy) {
        rerankingTimer.record(duration);
    }

    public void recordGeneration(Duration duration, String model) {
        generationTimer.record(duration);
    }

    public void recordTimeToFirstToken(Duration duration) {
        timeToFirstTokenTimer.record(duration);
    }

    public void recordTotalRequest(Duration duration, boolean isRefusal, boolean isError) {
        totalRequestTimer.record(duration);
        totalRequests.incrementAndGet();

        if (isError) {
            modelFailures.incrementAndGet();
            Counter.builder("rag.requests.completed")
                .tag("status", "ERROR")
                .register(meterRegistry)
                .increment();
        } else if (isRefusal) {
            refusals.incrementAndGet();
            Counter.builder("rag.requests.completed")
                .tag("status", "REFUSAL")
                .register(meterRegistry)
                .increment();
        } else {
            successfulResponses.incrementAndGet();
            Counter.builder("rag.requests.completed")
                .tag("status", "SUCCESS")
                .register(meterRegistry)
                .increment();
        }
    }

    public void recordTokenUsage(int promptTokens, int completionTokens, String model) {
        promptTokensSummary.record(promptTokens);
        completionTokensSummary.record(completionTokens);

        totalPromptTokens.addAndGet(promptTokens);
        totalCompletionTokens.addAndGet(completionTokens);

        double promptCost = (promptTokens / 1000.0) * INPUT_TOKEN_COST_PER_1K;
        double completionCost = (completionTokens / 1000.0) * OUTPUT_TOKEN_COST_PER_1K;
        double totalCallCost = promptCost + completionCost;

        totalEstimatedCostUsd.add(totalCallCost);

        Counter.builder("rag.inference.cost.usd")
            .tag("model", model != null ? model : "DEFAULT")
            .register(meterRegistry)
            .increment(totalCallCost);
    }

    public void recordEvidenceQuality(String level, double score) {
        if (level != null && evidenceQualityCounts.containsKey(level.toUpperCase())) {
            evidenceQualityCounts.get(level.toUpperCase()).incrementAndGet();
            Counter.builder("rag.evidence.quality.distribution")
                .tag("level", level.toUpperCase())
                .register(meterRegistry)
                .increment();
        }
    }

    public void recordModelFailure(String cause) {
        modelFailures.incrementAndGet();
        Counter.builder("rag.model.failures")
            .tag("cause", cause != null ? cause : "UNKNOWN")
            .register(meterRegistry)
            .increment();
    }

    // --- Performance Snapshot for Dashboard APIs ---

    public ObservabilitySnapshot getSnapshot() {
        double p50Req = getPercentile(totalRequestTimer, 0.50);
        double p95Req = getPercentile(totalRequestTimer, 0.95);
        double p99Req = getPercentile(totalRequestTimer, 0.99);

        double p50Ret = getPercentile(retrievalTimer, 0.50);
        double p95Ret = getPercentile(retrievalTimer, 0.95);

        double p50Gen = getPercentile(generationTimer, 0.50);
        double p95Gen = getPercentile(generationTimer, 0.95);

        double p50Ttft = getPercentile(timeToFirstTokenTimer, 0.50);
        double p95Ttft = getPercentile(timeToFirstTokenTimer, 0.95);

        long reqTotal = totalRequests.get();
        double successRate = reqTotal > 0 ? (successfulResponses.get() * 100.0 / reqTotal) : 100.0;
        double refusalRate = reqTotal > 0 ? (refusals.get() * 100.0 / reqTotal) : 0.0;
        double failureRate = reqTotal > 0 ? (modelFailures.get() * 100.0 / reqTotal) : 0.0;

        return new ObservabilitySnapshot(
            reqTotal,
            successfulResponses.get(),
            refusals.get(),
            modelFailures.get(),
            successRate,
            refusalRate,
            failureRate,
            p50Req,
            p95Req,
            p99Req,
            p50Ret,
            p95Ret,
            p50Gen,
            p95Gen,
            p50Ttft,
            p95Ttft,
            totalPromptTokens.get(),
            totalCompletionTokens.get(),
            totalEstimatedCostUsd.sum(),
            evidenceQualityCounts.get("HIGH").get(),
            evidenceQualityCounts.get("MEDIUM").get(),
            evidenceQualityCounts.get("LOW").get(),
            evidenceQualityCounts.get("INSUFFICIENT").get()
        );
    }

    private double getPercentile(Timer timer, double percentile) {
        if (timer == null || timer.count() == 0) {
            return 0.0;
        }
        for (io.micrometer.core.instrument.distribution.ValueAtPercentile vap : timer.takeSnapshot().percentileValues()) {
            if (Math.abs(vap.percentile() - percentile) < 0.01) {
                return vap.value(java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
        return timer.mean(java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public record ObservabilitySnapshot(
        long totalRequests,
        long successfulResponses,
        long refusals,
        long modelFailures,
        double successRatePercentage,
        double refusalRatePercentage,
        double errorRatePercentage,
        double p50TotalLatencyMs,
        double p95TotalLatencyMs,
        double p99TotalLatencyMs,
        double p50RetrievalLatencyMs,
        double p95RetrievalLatencyMs,
        double p50GenerationLatencyMs,
        double p95GenerationLatencyMs,
        double p50TimeToFirstTokenMs,
        double p95TimeToFirstTokenMs,
        long totalPromptTokens,
        long totalCompletionTokens,
        double totalEstimatedCostUsd,
        long highQualityCount,
        long mediumQualityCount,
        long lowQualityCount,
        long insufficientQualityCount
    ) {}
}
