package com.psplatform.backend.emailverification;

import java.time.OffsetDateTime;

public record EmailVerification(
	long id,
	String email,
	EmailVerificationPurpose purpose,
	String codeHash,
	OffsetDateTime expiresAt,
	int attemptCount,
	OffsetDateTime verifiedAt,
	String verificationTokenHash,
	OffsetDateTime tokenExpiresAt,
	OffsetDateTime consumedAt,
	OffsetDateTime requestedAt,
	OffsetDateTime createdAt,
	OffsetDateTime updatedAt
) {
}
