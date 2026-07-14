package com.psplatform.backend.emailverification;

public record EmailVerificationConfirmResponse(String verificationToken, long expiresInSeconds) {
}
