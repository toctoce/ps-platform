package com.psplatform.backend.auth;

public record PasswordResetRequest(String email, String verificationToken, String newPassword) {
}
