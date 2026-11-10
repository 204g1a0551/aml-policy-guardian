package com.bank.aml.unit;

import com.bank.aml.cache.TenantAwareCacheService;
import com.bank.aml.rag.ScoredChunk;
import com.bank.aml.security.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantAwareRedisCacheUnitTest {

    private TenantAwareCacheService cacheService;
    private SimpleMeterRegistry meterRegistry;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        // Using in-memory fallback mode (null RedisTemplate)
        cacheService = new TenantAwareCacheService(null, objectMapper, meterRegistry);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testTenantCacheIsolation() {
        TenantContext.setTenantId("bank-alpha");
        cacheService.put("retrieval", "query-sanctions", "Alpha Sanctions Policy Results", Duration.ofMinutes(10));

        // Read within same tenant: hit
        Optional<String> alphaHit = cacheService.get("retrieval", "query-sanctions", String.class);
        assertTrue(alphaHit.isPresent());
        assertEquals("Alpha Sanctions Policy Results", alphaHit.get());

        // Switch to different tenant: must be complete miss
        TenantContext.setTenantId("bank-beta");
        Optional<String> betaMiss = cacheService.get("retrieval", "query-sanctions", String.class);
        assertTrue(betaMiss.isEmpty(), "Cross-tenant cache leakage detected! bank-beta must not see bank-alpha data.");

        // bank-beta caches its own data
        cacheService.put("retrieval", "query-sanctions", "Beta Sanctions Policy Results", Duration.ofMinutes(10));
        Optional<String> betaHit = cacheService.get("retrieval", "query-sanctions", String.class);
        assertTrue(betaHit.isPresent());
        assertEquals("Beta Sanctions Policy Results", betaHit.get());

        // Switch back to bank-alpha: original value still preserved
        TenantContext.setTenantId("bank-alpha");
        Optional<String> alphaCheck = cacheService.get("retrieval", "query-sanctions", String.class);
        assertTrue(alphaCheck.isPresent());
        assertEquals("Alpha Sanctions Policy Results", alphaCheck.get());
    }

    @Test
    void testCacheHitAndMissMetrics() {
        TenantContext.setTenantId("metrics-bank");

        // Initial miss
        Optional<String> miss = cacheService.get("retrieval", "unknown-key", String.class);
        assertTrue(miss.isEmpty());
        assertEquals(1.0, meterRegistry.get("cache.retrieval.miss").counter().count());

        // Put and subsequent hit
        cacheService.put("retrieval", "known-key", "Known Value", Duration.ofMinutes(5));
        Optional<String> hit = cacheService.get("retrieval", "known-key", String.class);
        assertTrue(hit.isPresent());
        assertEquals(1.0, meterRegistry.get("cache.retrieval.hit").counter().count());
    }

    @Test
    void testNamespaceEviction() {
        TenantContext.setTenantId("evict-bank");
        cacheService.put("retrieval", "q1", "Result 1", Duration.ofMinutes(5));
        cacheService.put("retrieval", "q2", "Result 2", Duration.ofMinutes(5));
        cacheService.put("policies", "p1", "Policy 1", Duration.ofMinutes(5));

        // Evict only retrieval namespace
        cacheService.evictNamespace("retrieval");

        // retrieval entries must be gone
        assertTrue(cacheService.get("retrieval", "q1", String.class).isEmpty());
        assertTrue(cacheService.get("retrieval", "q2", String.class).isEmpty());

        // policies namespace must still exist
        assertTrue(cacheService.get("policies", "p1", String.class).isPresent());
        assertEquals(1.0, meterRegistry.get("cache.evictions").counter().count());
    }

    @Test
    void testGenericObjectSerializationWithScoredChunkList() {
        TenantContext.setTenantId("chunk-bank");

        UUID docId = UUID.randomUUID();
        ScoredChunk chunk1 = new ScoredChunk(
            UUID.randomUUID(), docId, "SAR Standard", "POLICY",
            "Filing deadline is 30 calendar days.", 0, 1, "SAR_RULES", "{}",
            null, false, null, 0.88, 0.75, 0.033, 0.92, "HYBRID_RERANK"
        );
        ScoredChunk chunk2 = new ScoredChunk(
            UUID.randomUUID(), docId, "SAR Standard", "POLICY",
            "Suspicious transactions exceeding 10000 require review.", 1, 2, "THRESHOLDS", "{}",
            null, false, null, 0.82, 0.65, 0.028, 0.85, "HYBRID_RERANK"
        );

        List<ScoredChunk> chunks = List.of(chunk1, chunk2);
        cacheService.put("retrieval", "sar-filing-window", chunks, Duration.ofMinutes(15));

        Optional<List<ScoredChunk>> cached = cacheService.getGeneric(
            "retrieval",
            "sar-filing-window",
            new TypeReference<List<ScoredChunk>>() {}
        );

        assertTrue(cached.isPresent());
        assertEquals(2, cached.get().size());
        assertEquals("SAR Standard", cached.get().get(0).getDocumentTitle());
        assertEquals("Filing deadline is 30 calendar days.", cached.get().get(0).getChunkText());
        assertEquals(0.92, cached.get().get(0).getRerankScore());
    }
}
