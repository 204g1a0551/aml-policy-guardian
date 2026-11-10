package com.bank.aml.unit;

import com.bank.aml.security.RateLimitingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitingUnitTest {

    private RateLimitingService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimitingService(3, 5, 2); // 3 login, 5 chat, 2 upload
    }

    @Test
    @DisplayName("Rate Limiter: Allows requests within threshold and decrements remaining")
    void testRateLimitWithinThreshold() {
        RateLimitingService.RateLimitDecision d1 = rateLimiter.checkLoginRateLimit("192.168.1.1");
        assertThat(d1.allowed()).isTrue();
        assertThat(d1.remaining()).isEqualTo(2);

        RateLimitingService.RateLimitDecision d2 = rateLimiter.checkLoginRateLimit("192.168.1.1");
        assertThat(d2.allowed()).isTrue();
        assertThat(d2.remaining()).isEqualTo(1);

        RateLimitingService.RateLimitDecision d3 = rateLimiter.checkLoginRateLimit("192.168.1.1");
        assertThat(d3.allowed()).isTrue();
        assertThat(d3.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Rate Limiter: Blocks requests exceeding threshold and returns 0 remaining")
    void testRateLimitExceeded() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLoginRateLimit("192.168.1.5");
        }

        RateLimitingService.RateLimitDecision d4 = rateLimiter.checkLoginRateLimit("192.168.1.5");
        assertThat(d4.allowed()).isFalse();
        assertThat(d4.remaining()).isEqualTo(0);
        assertThat(d4.limit()).isEqualTo(3);
        assertThat(d4.resetTimeEpochSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Rate Limiter: Isolates counters across different IP addresses")
    void testRateLimitIpIsolation() {
        for (int i = 0; i < 3; i++) {
            rateLimiter.checkLoginRateLimit("10.0.0.1");
        }
        assertThat(rateLimiter.checkLoginRateLimit("10.0.0.1").allowed()).isFalse();

        // Different IP is untouched and permitted
        assertThat(rateLimiter.checkLoginRateLimit("10.0.0.2").allowed()).isTrue();
    }
}
