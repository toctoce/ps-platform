package com.psplatform.backend.user;

public record PasswordChangeRequest(
	String currentPassword,
	String newPassword
) {
}
