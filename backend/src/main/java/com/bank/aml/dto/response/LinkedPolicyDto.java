package com.bank.aml.dto.response;

public record LinkedPolicyDto(
    String policyCode,
    String documentTitle,
    String section,
    String snippet,
    Double relevanceScore
) {}
