package com.bank.aml.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise sliding-window rate limiter protecting:
 * 1. Authentication brute-force attacks on /api/v1/auth/**
 * 2. Resource exhaustion / DoS attacks on /api/v1/chat/**
 * 3. File ingestion DoS attacks on /api/v1/documents/**
 */
@Service
public class RateLimitingService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingService.class);

    private final int maxLoginRequestsPerMinute;
    private final int maxChatRequestsPerMinute;
    private final int maxUploadRequestsPerMinute;

    public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        long resetTimeEpochSeconds
    ) {}

    private static class RequestCounter {
        long windowStartTimestamp;
        AtomicInteger count;

        RequestCounter(long timestamp) {
            this.windowStartTimestamp = timestamp;
            this.count = new AtomicInteger(1);
        }
    }

    private final Map<String, RequestCounter> loginCounters = new ConcurrentHashMap<>();
    private final Map<String, RequestCounter> chatCounters = new ConcurrentHashMap<>();
    private final Map<String, RequestCounter> uploadCounters = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public RateLimitingService(
        @Value("${app.security.rate-limiting.login-per-minute:15}") int maxLoginRequestsPerMinute,
        @Value("${app.security.rate-limiting.chat-per-minute:60}") int maxChatRequestsPerMinute,
        @Value("${app.security.rate-limiting.upload-per-minute:10}") int maxUploadRequestsPerMinute
    ) {
        this.maxLoginRequestsPerMinute = maxLoginRequestsPerMinute;
        this.maxChatRequestsPerMinute = maxChatRequestsPerMinute;
        this.maxUploadRequestsPerMinute = maxUploadRequestsPerMinute;
    }

    public RateLimitingService() {
        this(15, 60, 10);
    }

    public boolean allowLoginRequest(String clientIp) {
        return checkLoginRateLimit(clientIp).allowed();
    }

    public boolean allowChatRequest(String clientKey) {
        return checkChatRateLimit(clientKey).allowed();
    }

    public boolean allowUploadRequest(String clientKey) {
        return checkUploadRateLimit(clientKey).allowed();
    }

    public RateLimitDecision checkLoginRateLimit(String key) {
        return evaluateLimit(loginCounters, key, maxLoginRequestsPerMinute, "LOGIN_ATTEMPTS");
    }

    public RateLimitDecision checkChatRateLimit(String key) {
        return evaluateLimit(chatCounters, key, maxChatRequestsPerMinute, "CHAT_QUERIES");
    }

    public RateLimitDecision checkUploadRateLimit(String key) {
        return evaluateLimit(uploadCounters, key, maxUploadRequestsPerMinute, "DOCUMENT_UPLOADS");
    }

    private RateLimitDecision evaluateLimit(Map<String, RequestCounter> counters, String key, int maxRequests, String type) {
        long now = System.currentTimeMillis();
        long windowSizeMs = 60_000L; // 1 minute window

        RequestCounter counter = counters.compute(key, (k, current) -> {
            if (current == null || (now - current.windowStartTimestamp) > windowSizeMs) {
                return new RequestCounter(now);
            }
            current.count.incrementAndGet();
            return current;
        });

        int currentCount = counter.count.get();
        boolean allowed = currentCount <= maxRequests;
        int remaining = Math.max(0, maxRequests - currentCount);
        long resetEpoch = (counter.windowStartTimestamp + windowSizeMs) / 1000L;

        if (!allowed) {
            log.warn("RATE LIMIT EXCEEDED [{}]: client={}, count={}/{}", type, key.replaceAll("[\r\n]", "_"), currentCount, maxRequests);
        }

        return new RateLimitDecision(allowed, maxRequests, remaining, resetEpoch);
    }

    public void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        long windowSizeMs = 60_000L;
        loginCounters.entrySet().removeIf(entry -> (now - entry.getValue().windowStartTimestamp) > windowSizeMs);
        chatCounters.entrySet().removeIf(entry -> (now - entry.getValue().windowStartTimestamp) > windowSizeMs);
        uploadCounters.entrySet().removeIf(entry -> (now - entry.getValue().windowStartTimestamp) > windowSizeMs);
    }
}
