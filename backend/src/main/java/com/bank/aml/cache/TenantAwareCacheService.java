package com.bank.aml.cache;

import com.bank.aml.security.tenant.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TenantAwareCacheService {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareCacheService.class);

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // High-performance bounded in-memory fallback cache for when Redis is disabled or unreachable
    private final Map<String, CacheEntry> localFallbackCache = new ConcurrentHashMap<>();
    private static final int MAX_LOCAL_CACHE_SIZE = 5000;

    @Autowired
    public TenantAwareCacheService(
        @Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
        ObjectMapper objectMapper,
        @Autowired(required = false) MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    public boolean isRedisEnabled() {
        return redisTemplate != null;
    }

    /**
     * Reads a value from cache for the current tenant.
     */
    public <T> Optional<T> get(String namespace, String key, Class<T> targetClass) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = buildCacheKey(tenantId, namespace, key);

        if (redisTemplate != null) {
            try {
                Object raw = redisTemplate.opsForValue().get(cacheKey);
                if (raw != null) {
                    recordMetric("cache.retrieval.hit", "namespace", namespace, "tenant", tenantId);
                    T result = objectMapper.convertValue(raw, targetClass);
                    return Optional.ofNullable(result);
                }
            } catch (Exception ex) {
                log.warn("Redis lookup failed for key '{}': {}. Falling back to local cache.", cacheKey, ex.getMessage());
            }
        }

        // Local fallback lookup
        CacheEntry entry = localFallbackCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            recordMetric("cache.retrieval.hit", "namespace", namespace, "tenant", tenantId);
            T result = objectMapper.convertValue(entry.value, targetClass);
            return Optional.ofNullable(result);
        } else if (entry != null && entry.isExpired()) {
            localFallbackCache.remove(cacheKey);
        }

        recordMetric("cache.retrieval.miss", "namespace", namespace, "tenant", tenantId);
        return Optional.empty();
    }

    /**
     * Reads a generic typed value (e.g. List<ScoredChunk>) from cache.
     */
    public <T> Optional<T> getGeneric(String namespace, String key, TypeReference<T> typeRef) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = buildCacheKey(tenantId, namespace, key);

        if (redisTemplate != null) {
            try {
                Object raw = redisTemplate.opsForValue().get(cacheKey);
                if (raw != null) {
                    recordMetric("cache.retrieval.hit", "namespace", namespace, "tenant", tenantId);
                    T result = objectMapper.convertValue(raw, typeRef);
                    return Optional.ofNullable(result);
                }
            } catch (Exception ex) {
                log.warn("Redis lookup failed for key '{}': {}. Falling back to local cache.", cacheKey, ex.getMessage());
            }
        }

        // Local fallback
        CacheEntry entry = localFallbackCache.get(cacheKey);
        if (entry != null && !entry.isExpired()) {
            recordMetric("cache.retrieval.hit", "namespace", namespace, "tenant", tenantId);
            T result = objectMapper.convertValue(entry.value, typeRef);
            return Optional.ofNullable(result);
        } else if (entry != null && entry.isExpired()) {
            localFallbackCache.remove(cacheKey);
        }

        recordMetric("cache.retrieval.miss", "namespace", namespace, "tenant", tenantId);
        return Optional.empty();
    }

    /**
     * Puts a value into cache with a specified TTL for the current tenant.
     */
    public void put(String namespace, String key, Object value, Duration ttl) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = buildCacheKey(tenantId, namespace, key);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, value, ttl);
                return;
            } catch (Exception ex) {
                log.warn("Redis put failed for key '{}': {}. Writing to local cache.", cacheKey, ex.getMessage());
            }
        }

        // Local fallback insertion with bounds check
        if (localFallbackCache.size() >= MAX_LOCAL_CACHE_SIZE) {
            // Evict oldest/expired entries
            localFallbackCache.entrySet().removeIf(e -> e.getValue().isExpired());
            if (localFallbackCache.size() >= MAX_LOCAL_CACHE_SIZE) {
                Iterator<String> it = localFallbackCache.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
        }
        localFallbackCache.put(cacheKey, new CacheEntry(value, Instant.now().plus(ttl)));
    }

    /**
     * Evicts a single key in a namespace for current tenant.
     */
    public void evict(String namespace, String key) {
        String tenantId = TenantContext.getTenantId();
        String cacheKey = buildCacheKey(tenantId, namespace, key);

        if (redisTemplate != null) {
            try {
                redisTemplate.delete(cacheKey);
            } catch (Exception ex) {
                log.warn("Redis evict failed for key '{}': {}", cacheKey, ex.getMessage());
            }
        }

        localFallbackCache.remove(cacheKey);
        recordMetric("cache.evictions", "namespace", namespace, "tenant", tenantId);
    }

    /**
     * Evicts an entire namespace for the current tenant (e.g. "retrieval", "policies").
     */
    public void evictNamespace(String namespace) {
        String tenantId = TenantContext.getTenantId();
        evictNamespaceForTenant(tenantId, namespace);
    }

    /**
     * Evicts an entire namespace for a specified tenant.
     */
    public void evictNamespaceForTenant(String tenantId, String namespace) {
        String safeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default-bank";
        String pattern = "aml:tenant:" + safeTenant + ":" + namespace + ":*";

        if (redisTemplate != null) {
            try {
                Set<String> keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                    log.info("Evicted {} Redis keys for pattern: {}", keys.size(), pattern);
                }
            } catch (Exception ex) {
                log.warn("Redis namespace eviction failed for pattern '{}': {}", pattern, ex.getMessage());
            }
        }

        // Evict matching keys from local cache
        String prefix = "aml:tenant:" + safeTenant + ":" + namespace + ":";
        localFallbackCache.keySet().removeIf(k -> k.startsWith(prefix));

        recordMetric("cache.evictions", "namespace", namespace, "tenant", safeTenant);
    }

    /**
     * Evicts all cache partitions for a given tenant.
     */
    public void evictAllForTenant(String tenantId) {
        evictNamespaceForTenant(tenantId, "retrieval");
        evictNamespaceForTenant(tenantId, "policies");
        evictNamespaceForTenant(tenantId, "docs");
    }

    /**
     * Build tenant-isolated key format:
     * aml:tenant:{tenantId}:{namespace}:{sha256(key)}
     */
    public String buildCacheKey(String tenantId, String namespace, String key) {
        String safeTenant = (tenantId != null && !tenantId.isBlank()) ? tenantId : "default-bank";
        return "aml:tenant:" + safeTenant + ":" + namespace + ":" + hashKey(key);
    }

    private String hashKey(String input) {
        if (input == null || input.isBlank()) return "empty";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private void recordMetric(String name, String... tags) {
        if (meterRegistry != null) {
            try {
                Counter.builder(name).tags(tags).register(meterRegistry).increment();
            } catch (Exception ignored) {}
        }
    }

    private static class CacheEntry {
        final Object value;
        final Instant expiresAt;

        CacheEntry(Object value, Instant expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
