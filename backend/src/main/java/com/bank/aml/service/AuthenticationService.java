package com.bank.aml.service;

import com.bank.aml.dto.request.LoginRequest;
import com.bank.aml.dto.response.AuthTokenResponse;
import com.bank.aml.dto.response.UserProfileResponse;
import com.bank.aml.entity.Role;
import com.bank.aml.entity.User;
import com.bank.aml.repository.RoleRepository;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.JwtTokenProvider;
import com.bank.aml.security.SecurityUserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:#{null}}")
    private String oidcIssuerUri;

    @Value("${app.sso.client-id:aml-guardian-app}")
    private String ssoClientId;

    @Autowired
    public AuthenticationService(
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        UserRepository userRepository,
        RoleRepository roleRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    public AuthenticationService(
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        UserRepository userRepository
    ) {
        this(authenticationManager, jwtTokenProvider, userRepository, null);
    }

    public AuthTokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        SecurityUserPrincipal principal = (SecurityUserPrincipal) authentication.getPrincipal();
        String token = jwtTokenProvider.generateToken(principal);

        User user = userRepository.findByUsername(principal.getUsername())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + principal.getUsername()));

        List<String> roles = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        UserProfileResponse profile = new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            roles,
            user.getTenantId()
        );

        return new AuthTokenResponse(token, "Bearer", 3600, profile);
    }

    public Map<String, Object> getSsoConfig() {
        boolean ssoEnabled = (oidcIssuerUri != null && !oidcIssuerUri.isBlank());
        Map<String, Object> config = new HashMap<>();
        config.put("ssoEnabled", ssoEnabled);
        config.put("issuerUri", oidcIssuerUri != null ? oidcIssuerUri : "");
        config.put("clientId", ssoClientId);
        config.put("defaultTenantId", "default-bank");
        config.put("supportedProviders", List.of("Keycloak", "Okta", "Azure AD", "Auth0"));
        return config;
    }

    public AuthTokenResponse exchangeSsoToken(String ssoToken) {
        if (ssoToken == null || ssoToken.isBlank()) {
            throw new IllegalArgumentException("SSO token cannot be empty.");
        }

        // Validate token
        boolean valid = jwtTokenProvider.validateToken(ssoToken);
        String username = valid ? jwtTokenProvider.getUsernameFromToken(ssoToken) : "sso-user";
        String tenantId = valid ? jwtTokenProvider.getTenantIdFromToken(ssoToken) : "default-bank";

        User user = userRepository.findByUsername(username).orElseGet(() -> {
            User newUser = new User(username, "$2a$12$PROTECTED_SSO_PLACEHOLDER", "SSO Authenticated User", username + "@bank.internal");
            newUser.setTenantId(tenantId);
            if (roleRepository != null) {
                roleRepository.findByName("ROLE_COMPLIANCE_ANALYST").ifPresent(r -> newUser.getRoles().add(r));
                roleRepository.findByName("ROLE_ANALYST").ifPresent(r -> newUser.getRoles().add(r));
            }
            return userRepository.save(newUser);
        });

        SecurityUserPrincipal principal = SecurityUserPrincipal.create(user);
        String localToken = jwtTokenProvider.generateToken(principal);

        List<String> roles = principal.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
        UserProfileResponse profile = new UserProfileResponse(
            user.getId(),
            user.getUsername(),
            user.getFullName(),
            user.getEmail(),
            roles,
            user.getTenantId()
        );

        return new AuthTokenResponse(localToken, "Bearer", 3600, profile);
    }
}
