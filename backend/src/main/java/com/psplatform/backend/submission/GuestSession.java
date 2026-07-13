package com.psplatform.backend.submission;

import java.time.Instant;

public record GuestSession(
	String id,
	int submissionCount,
	Instant expiresAt
) {
	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}
}
