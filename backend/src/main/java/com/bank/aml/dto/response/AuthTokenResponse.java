package com.bank.aml.dto.response;

public record AuthTokenResponse(
    String accessToken,
    String tokenType,
    long expiresInSeconds,
    UserProfileResponse user
) {}
