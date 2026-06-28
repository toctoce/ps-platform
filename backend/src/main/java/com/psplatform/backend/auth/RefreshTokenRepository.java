package com.psplatform.backend.auth;

import java.util.Optional;

public interface RefreshTokenRepository {

	RefreshToken save(long userId, String tokenHash, java.time.LocalDateTime expiresAt);

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	void revokeAllByUserId(long userId);

	void revokeByTokenHash(String tokenHash);

	void deleteExpired();
}
