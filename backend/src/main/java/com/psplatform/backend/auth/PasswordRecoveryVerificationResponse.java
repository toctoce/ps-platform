package com.psplatform.backend.auth;

public record PasswordRecoveryVerificationResponse(String message, long expiresInSeconds) {
}
