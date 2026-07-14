package com.psplatform.backend.user;

public record UserSignupRequest(
	String email,
	String username,
	String password,
	String verificationToken
) {
}
