package com.psplatform.backend.auth;

import com.psplatform.backend.user.User;

public record AuthTokenPayload(
	String accessToken,
	String refreshToken,
	long expiresIn,
	User user
) {
}
