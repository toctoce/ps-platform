package com.psplatform.backend.user;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcUserRepository implements UserRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<User> rowMapper = (rs, rowNum) -> new User(
		rs.getLong("id"),
		rs.getString("email"),
		rs.getString("username"),
		rs.getString("password_hash"),
		rs.getString("role"),
		rs.getInt("auth_version"),
		rs.getTimestamp("created_at").toInstant(),
		rs.getTimestamp("updated_at").toInstant(),
		getTimestampOrNull(rs, "deleted_at"),
		getTimestampOrNull(rs, "suspended_at")
	);

	public JdbcUserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public User save(String email, String username, String passwordHash, String role) {
		String sql = """
			INSERT INTO users (email, username, username_normalized, password_hash, role, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
			ps.setString(1, email);
			ps.setString(2, username);
			ps.setString(3, UserUsername.normalizeForComparison(username));
			ps.setString(4, passwordHash);
			ps.setString(5, role);
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("User ID was not generated");
		}

		return findById(key.longValue())
			.orElseThrow(() -> new IllegalStateException("Saved user was not found"));
	}

	@Override
	public Optional<User> findById(long id) {
		List<User> users = jdbcTemplate.query(
			"""
			SELECT id, email, username, password_hash, role, auth_version,
			       created_at, updated_at, deleted_at, suspended_at
			FROM users
			WHERE id = ?
			""",
			rowMapper,
			id
		);
		return users.stream().findFirst();
	}

	@Override
	public Optional<User> findByEmail(String email) {
		List<User> users = jdbcTemplate.query(
			"""
			SELECT id, email, username, password_hash, role, auth_version,
			       created_at, updated_at, deleted_at, suspended_at
			FROM users
			WHERE email = ?
			""",
			rowMapper,
			email
		);
		return users.stream().findFirst();
	}

	@Override
	public Optional<User> findByUsername(String username) {
		List<User> users = jdbcTemplate.query(
			"""
			SELECT id, email, username, password_hash, role, auth_version,
			       created_at, updated_at, deleted_at, suspended_at
			FROM users
			WHERE username_normalized = ?
			""",
			rowMapper,
			UserUsername.normalizeForComparison(username)
		);
		return users.stream().findFirst();
	}

	@Override
	public boolean updateUsername(long id, String username) {
		return jdbcTemplate.update(
			"""
			UPDATE users
			SET username = ?, username_normalized = ?, updated_at = CURRENT_TIMESTAMP
			WHERE id = ? AND deleted_at IS NULL
			""",
			username,
			UserUsername.normalizeForComparison(username),
			id
		) == 1;
	}

	@Override
	public boolean updatePasswordHash(long id, String passwordHash) {
		return jdbcTemplate.update(
			"""
			UPDATE users
			SET password_hash = ?, auth_version = auth_version + 1, updated_at = CURRENT_TIMESTAMP
			WHERE id = ? AND deleted_at IS NULL
			""",
			passwordHash,
			id
		) == 1;
	}

	@Override
	public void deleteById(long id) {
		jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", id);
		jdbcTemplate.update(
			"""
			UPDATE users
			SET email = ('deleted-user-' || id || '@deleted.local'),
			    username = ('deleted-user-' || id),
			    username_normalized = LOWER('deleted-user-' || id),
			    password_hash = 'deleted-password',
			    role = 'WITHDRAWN',
			    deleted_at = CURRENT_TIMESTAMP,
			    updated_at = CURRENT_TIMESTAMP
			WHERE id = ? AND deleted_at IS NULL
			""",
			id
		);
	}

	private static java.time.Instant getTimestampOrNull(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM admin_audit_logs");
		jdbcTemplate.update("DELETE FROM user_experience_awards");
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM users");
	}
}
