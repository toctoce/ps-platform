package com.psplatform.backend.auth;

import java.time.LocalDateTime;

public class RefreshToken {

	private final Long id;
	private final Long userId;
	private final String tokenHash;
	private final LocalDateTime expiresAt;
	private final boolean revoked;
	private final LocalDateTime createdAt;

	public RefreshToken(
		Long id,
		Long userId,
		String tokenHash,
		LocalDateTime expiresAt,
		boolean revoked,
		LocalDateTime createdAt
	) {
		this.id = id;
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.revoked = revoked;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public boolean isRevoked() {
		return revoked;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
