package com.bank.aml.service;

import com.bank.aml.dto.request.LoginRequest;
import com.bank.aml.dto.response.AuthTokenResponse;
import com.bank.aml.dto.response.UserProfileResponse;
import com.bank.aml.entity.User;
import com.bank.aml.repository.UserRepository;
import com.bank.aml.security.JwtTokenProvider;
import com.bank.aml.security.SecurityUserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthenticationService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    public AuthenticationService(
        AuthenticationManager authenticationManager,
        JwtTokenProvider jwtTokenProvider,
        UserRepository userRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenProvider = jwtTokenProvider;
        this.userRepository = userRepository;
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
            roles
        );

        return new AuthTokenResponse(token, "Bearer", 3600, profile);
    }
}
