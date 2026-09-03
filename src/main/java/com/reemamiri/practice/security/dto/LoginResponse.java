package com.reemamiri.practice.security.dto;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, String displayName) {}
