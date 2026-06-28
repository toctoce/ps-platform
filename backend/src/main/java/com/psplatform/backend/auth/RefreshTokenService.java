package com.psplatform.backend.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import org.springframework.stereotype.Service;

import com.psplatform.backend.user.User;

@Service
public class RefreshTokenService {

	private static final String TOKEN_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_";
	private static final int TOKEN_LENGTH = 64;

	private final AuthTokenConfig tokenConfig;
	private final RefreshTokenRepository refreshTokenRepository;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(AuthTokenConfig tokenConfig, RefreshTokenRepository refreshTokenRepository) {
		this.tokenConfig = tokenConfig;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	public TokenPair createForUser(User user) {
		refreshTokenRepository.revokeAllByUserId(user.getId());

		String refreshToken = generateRandomToken();
		String tokenHash = hashToken(refreshToken);
		LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
			.plusSeconds(tokenConfig.getRefreshTokenTtlSeconds());
		refreshTokenRepository.save(user.getId(), tokenHash, expiresAt);

		return new TokenPair(
			null,
			refreshToken,
			tokenConfig.getRefreshTokenTtlSeconds()
		);
	}

	public RefreshToken assertValidAndGet(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new RefreshTokenInvalidException();
		}

		RefreshToken token = refreshTokenRepository.findByTokenHash(hashToken(refreshToken))
			.orElseThrow(RefreshTokenInvalidException::new);

		if (token.isRevoked()) {
			throw new RefreshTokenInvalidException();
		}

		if (token.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
			throw new RefreshTokenExpiredException();
		}

		return token;
	}

	public TokenPair rotateForRefresh(String refreshToken) {
		RefreshToken token = assertValidAndGet(refreshToken);
		refreshTokenRepository.revokeByTokenHash(hashToken(refreshToken));
		return createForUserId(token.getUserId());
	}

	public TokenPair createForUserId(long userId) {
		String token = generateRandomToken();
		String tokenHash = hashToken(token);
		LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC)
			.plusSeconds(tokenConfig.getRefreshTokenTtlSeconds());
		refreshTokenRepository.save(userId, tokenHash, expiresAt);
		return new TokenPair(null, token, tokenConfig.getRefreshTokenTtlSeconds());
	}

	public void revoke(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new RefreshTokenInvalidException();
		}
		refreshTokenRepository.revokeByTokenHash(hashToken(refreshToken));
	}

	public String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}

	private String generateRandomToken() {
		StringBuilder token = new StringBuilder(TOKEN_LENGTH);
		for (int i = 0; i < TOKEN_LENGTH; i++) {
			int index = secureRandom.nextInt(TOKEN_ALPHABET.length());
			token.append(TOKEN_ALPHABET.charAt(index));
		}
		return token.toString();
	}
}
