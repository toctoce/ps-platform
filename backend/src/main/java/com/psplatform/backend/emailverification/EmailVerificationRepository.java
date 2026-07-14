package com.psplatform.backend.emailverification;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface EmailVerificationRepository {

	Optional<EmailVerification> findByEmailAndPurpose(String email, EmailVerificationPurpose purpose);

	void saveOrReplace(
		String email,
		EmailVerificationPurpose purpose,
		String codeHash,
		OffsetDateTime expiresAt,
		OffsetDateTime now
	);

	boolean verify(
		String email,
		EmailVerificationPurpose purpose,
		String codeHash,
		String tokenHash,
		OffsetDateTime tokenExpiresAt,
		OffsetDateTime now,
		int maxAttempts
	);

	void incrementAttempt(long id, int maxAttempts, OffsetDateTime now);

	boolean consumeToken(
		String email,
		EmailVerificationPurpose purpose,
		String tokenHash,
		OffsetDateTime now
	);

	void deleteByEmailAndPurposeAndCodeHash(
		String email,
		EmailVerificationPurpose purpose,
		String codeHash
	);

	void deleteAll();
}
