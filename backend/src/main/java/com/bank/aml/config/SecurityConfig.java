package com.bank.aml.config;

import com.bank.aml.security.JwtAuthenticationFilter;
import com.bank.aml.security.RateLimitingFilter;
import com.bank.aml.security.RestAccessDeniedHandler;
import com.bank.aml.security.oauth2.CustomJwtAuthenticationConverter;
import com.bank.aml.security.tenant.TenantContextFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final TenantContextFilter tenantContextFilter;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final CustomJwtAuthenticationConverter customJwtAuthenticationConverter;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    private String oidcIssuerUri;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        RateLimitingFilter rateLimitingFilter,
        TenantContextFilter tenantContextFilter,
        RestAccessDeniedHandler restAccessDeniedHandler,
        CustomJwtAuthenticationConverter customJwtAuthenticationConverter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.tenantContextFilter = tenantContextFilter;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.customJwtAuthenticationConverter = customJwtAuthenticationConverter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.accessDeniedHandler(restAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                // Allow public health/prometheus scraper probes and authentication
                .requestMatchers("/actuator/health", "/actuator/prometheus", "/api/v1/auth/**").permitAll()
                // All other actuator metrics/internals restricted to ADMIN
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                // Audit and security intelligence restricted to ADMIN and AUDITOR
                .requestMatchers(
                    "/api/v1/admin/audit", "/api/v1/admin/audit/**",
                    "/api/v1/admin/security", "/api/v1/admin/security/**",
                    "/api/v1/admin/evaluation", "/api/v1/admin/evaluation/**"
                ).hasAnyRole("ADMIN", "AUDITOR")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Document uploads/deletions restricted to ADMIN
                .requestMatchers(HttpMethod.POST, "/api/v1/documents", "/api/v1/documents/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/documents", "/api/v1/documents/**").hasRole("ADMIN")
                // Document viewing allowed for ADMIN, ANALYST, and AUDITOR
                .requestMatchers(HttpMethod.GET, "/api/v1/documents", "/api/v1/documents/**").hasAnyRole("ADMIN", "ANALYST", "COMPLIANCE_ANALYST", "AUDITOR")
                // Chat queries and SSE streaming allowed for ADMIN and ANALYST
                .requestMatchers("/api/v1/chat", "/api/v1/chat/**").hasAnyRole("ADMIN", "ANALYST", "COMPLIANCE_ANALYST")
                // Policy versioning, regulatory change intelligence & comparisons
                .requestMatchers(HttpMethod.POST, "/api/v1/policies/*/approve").hasRole("ADMIN")
                .requestMatchers("/api/v1/policies", "/api/v1/policies/**").hasAnyRole("ADMIN", "ANALYST", "COMPLIANCE_ANALYST", "AUDITOR")
                // Investigation workspace and human review workflows
                .requestMatchers(HttpMethod.POST, "/api/v1/investigations/*/reviewer-decision").hasRole("ADMIN")
                .requestMatchers("/api/v1/investigations", "/api/v1/investigations/**").hasAnyRole("ADMIN", "ANALYST", "COMPLIANCE_ANALYST", "AUDITOR")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(customJwtAuthenticationConverter)
                )
                .accessDeniedHandler(restAccessDeniedHandler)
            )
            .addFilterBefore(tenantContextFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (oidcIssuerUri != null && !oidcIssuerUri.isBlank()) {
            return JwtDecoders.fromIssuerLocation(oidcIssuerUri);
        }
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "HmacSHA512");
        return NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS512)
            .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost*",
            "https://localhost*",
            "http://127.0.0.1*",
            "https://127.0.0.1*",
            "https://*.loca.lt*",
            "https://*.ngrok-free.app*",
            "https://*.trycloudflare.com*",
            "https://*.amazonaws.com*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-Correlation-ID", "X-Tenant-ID"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID", "X-Tenant-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization", "Link", "X-Total-Count", "X-Correlation-ID", "X-Tenant-ID", "X-RateLimit-Limit", "X-RateLimit-Remaining", "X-RateLimit-Reset"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
