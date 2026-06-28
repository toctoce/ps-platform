package com.psplatform.backend.auth;

import com.psplatform.backend.user.UserResponse;

public record AuthTokenResponse(
	String tokenType,
	String accessToken,
	long expiresIn,
	String refreshToken,
	UserResponse user
) {

	public static AuthTokenResponse from(AuthTokenPayload payload) {
		return new AuthTokenResponse(
			"Bearer",
			payload.accessToken(),
			payload.expiresIn(),
			payload.refreshToken(),
			UserResponse.from(payload.user())
		);
	}
}
