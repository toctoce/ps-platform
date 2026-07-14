package com.psplatform.backend.emailverification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEmailVerificationRepository implements EmailVerificationRepository {

	private final JdbcTemplate jdbcTemplate;
	private final RowMapper<EmailVerification> rowMapper = (resultSet, rowNumber) -> new EmailVerification(
		resultSet.getLong("id"),
		resultSet.getString("email"),
		EmailVerificationPurpose.valueOf(resultSet.getString("purpose")),
		resultSet.getString("code_hash"),
		resultSet.getObject("expires_at", OffsetDateTime.class),
		resultSet.getInt("attempt_count"),
		getOffsetDateTime(resultSet, "verified_at"),
		resultSet.getString("verification_token_hash"),
		getOffsetDateTime(resultSet, "token_expires_at"),
		getOffsetDateTime(resultSet, "consumed_at"),
		resultSet.getObject("requested_at", OffsetDateTime.class),
		resultSet.getObject("created_at", OffsetDateTime.class),
		resultSet.getObject("updated_at", OffsetDateTime.class)
	);

	public JdbcEmailVerificationRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Optional<EmailVerification> findByEmailAndPurpose(
		String email,
		EmailVerificationPurpose purpose
	) {
		List<EmailVerification> verifications = jdbcTemplate.query(
			"""
			SELECT id, email, purpose, code_hash, expires_at, attempt_count,
			       verified_at, verification_token_hash, token_expires_at, consumed_at, requested_at,
			       created_at, updated_at
			FROM email_verifications
			WHERE email = ? AND purpose = ?
			""",
			rowMapper,
			email,
			purpose.name()
		);
		return verifications.stream().findFirst();
	}

	@Override
	public void saveOrReplace(
		String email,
		EmailVerificationPurpose purpose,
		String codeHash,
		OffsetDateTime expiresAt,
		OffsetDateTime now
	) {
		int updated = updateVerification(email, purpose, codeHash, expiresAt, now);
		if (updated > 0) {
			return;
		}

		try {
			jdbcTemplate.update(
				"""
				INSERT INTO email_verifications (
				    email, purpose, code_hash, expires_at, attempt_count, requested_at, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, 0, ?, ?, ?)
				""",
				email,
				purpose.name(),
				codeHash,
				expiresAt,
				now,
				now,
				now
			);
		} catch (DuplicateKeyException exception) {
			updateVerification(email, purpose, codeHash, expiresAt, now);
		}
	}

	private int updateVerification(
		String email,
		EmailVerificationPurpose purpose,
		String codeHash,
		OffsetDateTime expiresAt,
		OffsetDateTime now
	) {
		return jdbcTemplate.update(
			"""
			UPDATE email_verifications
			SET code_hash = ?, expires_at = ?, attempt_count = 0,
			    verified_at = NULL, verification_token_hash = NULL,
			    token_expires_at = NULL, consumed_at = NULL, requested_at = ?, updated_at = ?
			WHERE email = ? AND purpose = ?
			""",
			codeHash,
			expiresAt,
			now,
			now,
			email,
			purpose.name()
		);
	}

	@Override
	public boolean verify(
		String email,
		EmailVerificationPurpose purpose,
		String codeHash,
		String tokenHash,
		OffsetDateTime tokenExpiresAt,
		OffsetDateTime now,
		int maxAttempts
	) {
		return jdbcTemplate.update(
			"""
			UPDATE email_verifications
			SET verified_at = ?, verification_token_hash = ?, token_expires_at = ?, updated_at = ?
			WHERE email = ? AND purpose = ? AND code_hash = ?
			  AND expires_at > ? AND attempt_count < ?
			  AND verified_at IS NULL AND consumed_at IS NULL
			""",
			now,
			tokenHash,
			tokenExpiresAt,
			now,
			email,
			purpose.name(),
			codeHash,
			now,
			maxAttempts
		) == 1;
	}

	@Override
	public void incrementAttempt(long id, int maxAttempts, OffsetDateTime now) {
		jdbcTemplate.update(
			"""
			UPDATE email_verifications
			SET attempt_count = attempt_count + 1, updated_at = ?
			WHERE id = ? AND attempt_count < ? AND verified_at IS NULL
			""",
			now,
			id,
			maxAttempts
		);
	}

	@Override
	public boolean consumeToken(
		String email,
		EmailVerificationPurpose purpose,
		String tokenHash,
		OffsetDateTime now
	) {
		return jdbcTemplate.update(
			"""
			UPDATE email_verifications
			SET consumed_at = ?, updated_at = ?
			WHERE email = ? AND purpose = ? AND verification_token_hash = ?
			  AND verified_at IS NOT NULL AND token_expires_at > ? AND consumed_at IS NULL
			""",
			now,
			now,
			email,
			purpose.name(),
			tokenHash,
			now
		) == 1;
	}

	@Override
	public void deleteByEmailAndPurposeAndCodeHash(
		String email,
		EmailVerificationPurpose purpose,
		String codeHash
	) {
		jdbcTemplate.update(
			"DELETE FROM email_verifications WHERE email = ? AND purpose = ? AND code_hash = ?",
			email,
			purpose.name(),
			codeHash
		);
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM email_verifications");
	}

	private static OffsetDateTime getOffsetDateTime(ResultSet resultSet, String column) throws SQLException {
		return resultSet.getObject(column, OffsetDateTime.class);
	}
}
