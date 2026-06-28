package com.psplatform.backend.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthTokenConfig {

	private final String jwtSecret;
	private final long accessTokenTtlSeconds;
	private final long refreshTokenTtlSeconds;

	public AuthTokenConfig(
		@Value("${app.auth.jwt.secret}") String jwtSecret,
		@Value("${app.auth.jwt.access-token-ttl-seconds}") long accessTokenTtlSeconds,
		@Value("${app.auth.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds
	) {
		this.jwtSecret = jwtSecret;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
	}

	public String getJwtSecret() {
		return jwtSecret;
	}

	public long getAccessTokenTtlSeconds() {
		return accessTokenTtlSeconds;
	}

	public long getRefreshTokenTtlSeconds() {
		return refreshTokenTtlSeconds;
	}
}
