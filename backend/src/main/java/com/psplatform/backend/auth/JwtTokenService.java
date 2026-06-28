package com.psplatform.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.psplatform.backend.user.User;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;

import io.jsonwebtoken.ExpiredJwtException;

@Service
public class JwtTokenService {

	private final AuthTokenConfig tokenConfig;
	private final SecretKey signingKey;

	public JwtTokenService(AuthTokenConfig tokenConfig) {
		this.tokenConfig = tokenConfig;
		if (tokenConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
			throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
		}
		this.signingKey = Keys.hmacShaKeyFor(tokenConfig.getJwtSecret().getBytes(StandardCharsets.UTF_8));
	}

	public String createAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plusSeconds(tokenConfig.getAccessTokenTtlSeconds());

		return Jwts.builder()
			.setClaims(Map.of(
				"email", user.getEmail(),
				"role", user.getRole()
			))
			.setSubject(String.valueOf(user.getId()))
			.setIssuedAt(Date.from(now))
			.setExpiration(Date.from(expiresAt))
			.signWith(signingKey)
			.compact();
	}

	public long getAccessTokenExpiresInSeconds() {
		return tokenConfig.getAccessTokenTtlSeconds();
	}

	public JwtClaims parseAccessToken(String token) {
		try {
			Jws<Claims> claims = Jwts.parserBuilder()
				.setSigningKey(signingKey)
				.build()
				.parseClaimsJws(token);

			Claims body = claims.getBody();
			return new JwtClaims(
				Long.parseLong(body.getSubject()),
				body.get("email", String.class),
				body.get("role", String.class)
			);
		} catch (ExpiredJwtException e) {
			throw new InvalidAccessTokenException("Access token expired");
		} catch (SignatureException e) {
			throw new InvalidAccessTokenException("Invalid access token signature");
		} catch (Exception e) {
			throw new InvalidAccessTokenException("Invalid access token");
		}
	}
}
