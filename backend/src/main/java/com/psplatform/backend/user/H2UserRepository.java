package com.psplatform.backend.user;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class H2UserRepository implements UserRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<User> rowMapper = (rs, rowNum) -> new User(
		rs.getLong("id"),
		rs.getString("email"),
		rs.getString("username"),
		rs.getString("password_hash"),
		rs.getString("role"),
		rs.getTimestamp("created_at").toLocalDateTime(),
		rs.getTimestamp("updated_at").toLocalDateTime()
	);

	public H2UserRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public User save(String email, String username, String passwordHash, String role) {
		String sql = """
			INSERT INTO users (email, username, password_hash, role, created_at, updated_at)
			VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
			ps.setString(1, email);
			ps.setString(2, username);
			ps.setString(3, passwordHash);
			ps.setString(4, role);
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
			SELECT id, email, username, password_hash, role, created_at, updated_at
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
			SELECT id, email, username, password_hash, role, created_at, updated_at
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
			SELECT id, email, username, password_hash, role, created_at, updated_at
			FROM users
			WHERE username = ?
			""",
			rowMapper,
			username
		);
		return users.stream().findFirst();
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM refresh_tokens");
		jdbcTemplate.update("DELETE FROM users");
	}
}
