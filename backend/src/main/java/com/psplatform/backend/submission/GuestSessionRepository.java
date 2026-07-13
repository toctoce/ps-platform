package com.psplatform.backend.submission;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

public interface GuestSessionRepository {

	GuestSession save(String id, Instant expiresAt);

	Optional<GuestSession> findById(String id);

	OptionalInt incrementSubmissionCount(String id, int limit);

	void deleteAll();
}
