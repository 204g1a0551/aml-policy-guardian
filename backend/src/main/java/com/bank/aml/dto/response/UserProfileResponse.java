package com.bank.aml.dto.response;

import java.util.List;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    String fullName,
    String email,
    List<String> roles
) {}
