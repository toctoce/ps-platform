package com.psplatform.backend.auth;

public record TokenPair(
	String accessToken,
	String refreshToken,
	long expiresIn
) {
}
