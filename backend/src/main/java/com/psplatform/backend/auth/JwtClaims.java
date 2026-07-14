package com.psplatform.backend.auth;

public record JwtClaims(
	long userId,
	String email,
	String role,
	int authVersion
) {
}
