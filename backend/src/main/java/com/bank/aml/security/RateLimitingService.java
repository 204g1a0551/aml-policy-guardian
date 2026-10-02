package com.bank.aml.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory sliding-window rate limiter to protect against:
 * 1. Authentication brute-force attacks on /api/v1/auth/login
 * 2. Resource exhaustion / DoS attacks on /api/v1/chat/**
 */
@Service
public class RateLimitingService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingService.class);

    private static final int MAX_LOGIN_REQUESTS_PER_MINUTE = 15;
    private static final int MAX_CHAT_REQUESTS_PER_MINUTE = 60;

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

    /**
     * Checks if a login request is permitted for the given client IP.
     */
    public boolean allowLoginRequest(String clientIp) {
        return checkRateLimit(loginCounters, clientIp, MAX_LOGIN_REQUESTS_PER_MINUTE, "LOGIN_ATTEMPTS");
    }

    /**
     * Checks if a chat request is permitted for the given client IP or user.
     */
    public boolean allowChatRequest(String clientKey) {
        return checkRateLimit(chatCounters, clientKey, MAX_CHAT_REQUESTS_PER_MINUTE, "CHAT_QUERIES");
    }

    private boolean checkRateLimit(Map<String, RequestCounter> counters, String key, int maxRequests, String type) {
        long now = System.currentTimeMillis();
        long windowSizeMs = 60_000L; // 1 minute window

        RequestCounter counter = counters.compute(key, (k, current) -> {
            if (current == null || (now - current.windowStartTimestamp) > windowSizeMs) {
                return new RequestCounter(now);
            }
            current.count.incrementAndGet();
            return current;
        });

        if (counter.count.get() > maxRequests) {
            log.warn("RATE LIMIT EXCEEDED [{}]: client={}, count={}/{}", type, key.replaceAll("[\r\n]", "_"), counter.count.get(), maxRequests);
            return false;
        }

        return true;
    }

    /**
     * Clear expired counters periodically to prevent memory growth.
     */
    public void cleanupStaleEntries() {
        long now = System.currentTimeMillis();
        long windowSizeMs = 60_000L;
        loginCounters.entrySet().removeIf(entry -> (now - entry.getValue().windowStartTimestamp) > windowSizeMs);
        chatCounters.entrySet().removeIf(entry -> (now - entry.getValue().windowStartTimestamp) > windowSizeMs);
    }
}
