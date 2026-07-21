package com.psplatform.backend.auth;

import com.psplatform.backend.user.UserResponse;
import com.psplatform.backend.experience.UserLevelProgress;

public record AuthTokenResponse(
	String tokenType,
	String accessToken,
	long expiresIn,
	String refreshToken,
	UserResponse user
) {

	public static AuthTokenResponse from(AuthTokenPayload payload, UserLevelProgress progress) {
		return new AuthTokenResponse(
			"Bearer",
			payload.accessToken(),
			payload.expiresIn(),
			payload.refreshToken(),
			UserResponse.from(payload.user(), progress)
		);
	}
}
