package com.psplatform.backend.submission;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcGuestSessionRepository implements GuestSessionRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcGuestSessionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public GuestSession save(String id, Instant expiresAt) {
		jdbcTemplate.update(
			"""
			INSERT INTO guest_sessions (id, submission_count, expires_at)
			VALUES (?, 0, ?)
			""",
			id,
			Timestamp.from(expiresAt)
		);
		return findById(id)
			.orElseThrow(() -> new IllegalStateException("Saved guest session was not found"));
	}

	@Override
	public Optional<GuestSession> findById(String id) {
		if (id == null || id.isBlank()) {
			return Optional.empty();
		}
		List<GuestSession> sessions = jdbcTemplate.query(
			"""
			SELECT id, submission_count, expires_at
			FROM guest_sessions
			WHERE id = ?
			""",
			(rs, rowNum) -> new GuestSession(
				rs.getString("id"),
				rs.getInt("submission_count"),
				rs.getTimestamp("expires_at").toInstant()
			),
			id
		);
		return sessions.stream().findFirst();
	}

	@Override
	public OptionalInt incrementSubmissionCount(String id, int limit) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE guest_sessions
			SET submission_count = submission_count + 1,
			    updated_at = CURRENT_TIMESTAMP
			WHERE id = ?
			  AND expires_at > CURRENT_TIMESTAMP
			  AND submission_count < ?
			""",
			id,
			limit
		);
		if (updated == 0) {
			return OptionalInt.empty();
		}
		Integer count = jdbcTemplate.queryForObject(
			"SELECT submission_count FROM guest_sessions WHERE id = ?",
			Integer.class,
			id
		);
		return count == null ? OptionalInt.empty() : OptionalInt.of(count);
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM guest_sessions");
	}
}
