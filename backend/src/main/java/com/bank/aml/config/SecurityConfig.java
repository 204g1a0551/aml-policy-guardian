package com.bank.aml.config;

import com.bank.aml.security.JwtAuthenticationFilter;
import com.bank.aml.security.RateLimitingFilter;
import com.bank.aml.security.RestAccessDeniedHandler;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        RateLimitingFilter rateLimitingFilter,
        RestAccessDeniedHandler restAccessDeniedHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex.accessDeniedHandler(restAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                // Allow only public health probe and authentication
                .requestMatchers("/actuator/health", "/api/v1/auth/**").permitAll()
                // All other actuator metrics/internals restricted to ADMIN
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/v1/documents/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/documents/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/documents/**").hasAnyRole("ADMIN", "ANALYST")
                .requestMatchers("/api/v1/chat/**").hasAnyRole("ADMIN", "ANALYST")
                .anyRequest().authenticated()
            )
            .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Strict origin whitelist to prevent cross-origin data exfiltration
        config.setAllowedOrigins(List.of(
            "http://localhost:4200",
            "http://127.0.0.1:4200",
            "http://localhost",
            "https://localhost",
            "http://localhost:80"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "X-Correlation-ID"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization", "Link", "X-Total-Count", "X-Correlation-ID"));

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
