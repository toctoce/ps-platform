package com.psplatform.backend.emailverification;

public record EmailVerificationRequestResponse(String message, long expiresInSeconds) {
}
