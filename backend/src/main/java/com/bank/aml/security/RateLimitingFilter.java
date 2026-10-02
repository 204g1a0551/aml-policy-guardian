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
 * Filter that enforces rate limiting on sensitive authentication and RAG endpoints.
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
        String clientIp = extractClientIp(request);

        if (path.startsWith("/api/v1/auth/login")) {
            if (!rateLimitingService.allowLoginRequest(clientIp)) {
                sendRateLimitResponse(response, "Too many login attempts. Please try again after 60 seconds.");
                return;
            }
        } else if (path.startsWith("/api/v1/chat")) {
            if (!rateLimitingService.allowChatRequest(clientIp)) {
                sendRateLimitResponse(response, "Too many chat queries. Please try again shortly.");
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
