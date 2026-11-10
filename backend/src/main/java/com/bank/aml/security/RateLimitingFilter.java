package com.bank.aml.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;

/**
 * Filter that enforces rate limiting on sensitive authentication, document ingestion, and RAG endpoints,
 * emitting standard IETF X-RateLimit headers.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;

    public RateLimitingFilter(RateLimitingService rateLimitingService, ObjectMapper objectMapper) {
        this.rateLimitingService = rateLimitingService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = extractClientIp(request);

        RateLimitingService.RateLimitDecision decision = null;

        if (path.startsWith("/api/v1/auth/login")) {
            decision = rateLimitingService.checkLoginRateLimit(clientIp);
        } else if (path.startsWith("/api/v1/chat")) {
            decision = rateLimitingService.checkChatRateLimit(clientIp);
        } else if (path.startsWith("/api/v1/documents") && "POST".equalsIgnoreCase(method)) {
            decision = rateLimitingService.checkUploadRateLimit(clientIp);
        }

        if (decision != null) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
            response.setHeader("X-RateLimit-Reset", String.valueOf(decision.resetTimeEpochSeconds()));

            if (!decision.allowed()) {
                sendRateLimitResponse(response, "Rate limit exceeded. Please wait before retrying.");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendRateLimitResponse(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, detail);
        problem.setTitle("Too Many Requests");
        problem.setType(URI.create("https://api.bank.internal/errors/too-many-requests"));
        problem.setProperty("code", "AML-SEC-4029");
        problem.setProperty("timestamp", OffsetDateTime.now());

        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown-ip";
    }
}
