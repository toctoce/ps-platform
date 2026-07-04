package com.psplatform.backend.auth;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcRefreshTokenRepository implements RefreshTokenRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<RefreshToken> rowMapper = (rs, rowNum) -> new RefreshToken(
		rs.getLong("id"),
		rs.getLong("user_id"),
		rs.getString("token_hash"),
		rs.getTimestamp("expires_at").toLocalDateTime(),
		rs.getBoolean("revoked"),
		rs.getTimestamp("created_at").toLocalDateTime()
	);

	public JdbcRefreshTokenRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public RefreshToken save(long userId, String tokenHash, LocalDateTime expiresAt) {
		String sql = """
			INSERT INTO refresh_tokens (user_id, token_hash, expires_at, revoked, created_at)
			VALUES (?, ?, ?, false, CURRENT_TIMESTAMP)
			""";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
			ps.setLong(1, userId);
			ps.setString(2, tokenHash);
			ps.setTimestamp(3, Timestamp.valueOf(expiresAt));
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Refresh token ID was not generated");
		}

		return findByTokenHash(tokenHash)
			.orElseThrow(() -> new IllegalStateException("Saved refresh token was not found"));
	}

	@Override
	public Optional<RefreshToken> findByTokenHash(String tokenHash) {
		List<RefreshToken> tokens = jdbcTemplate.query(
			"""
			SELECT id, user_id, token_hash, expires_at, revoked, created_at
			FROM refresh_tokens
			WHERE token_hash = ?
			""",
			rowMapper,
			tokenHash
		);
		return tokens.stream().findFirst();
	}

	@Override
	public void revokeAllByUserId(long userId) {
		jdbcTemplate.update(
			"UPDATE refresh_tokens SET revoked = true WHERE user_id = ?",
			userId
		);
	}

	@Override
	public void revokeByTokenHash(String tokenHash) {
		jdbcTemplate.update(
			"UPDATE refresh_tokens SET revoked = true WHERE token_hash = ?",
			tokenHash
		);
	}

	@Override
	public void deleteExpired() {
		jdbcTemplate.update(
			"DELETE FROM refresh_tokens WHERE expires_at < CURRENT_TIMESTAMP OR revoked = true"
		);
	}
}
