package com.bank.aml.controller;

import com.bank.aml.dto.request.LoginRequest;
import com.bank.aml.dto.response.AuthTokenResponse;
import com.bank.aml.dto.response.UserProfileResponse;
import com.bank.aml.security.SecurityUserPrincipal;
import com.bank.aml.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthTokenResponse response = authenticationService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        List<String> roles = principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();

        UserProfileResponse profile = new UserProfileResponse(
            principal.getId(),
            principal.getUsername(),
            principal.getUsername(), // Display name fallback
            principal.getUsername() + "@bank.internal",
            roles
        );
        return ResponseEntity.ok(profile);
    }
}
