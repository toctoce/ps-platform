package com.psplatform.backend.auth;

public record PasswordRecoveryConfirmRequest(String email, String code) {
}
