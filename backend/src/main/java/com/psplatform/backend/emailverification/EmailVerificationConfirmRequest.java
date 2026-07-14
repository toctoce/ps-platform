package com.psplatform.backend.emailverification;

public record EmailVerificationConfirmRequest(String email, String code) {
}
