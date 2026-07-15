package com.psplatform.backend.submission;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalInt;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class GuestSessionService {

	// 비회원 제출 제한 정책이 확정되기 전까지 사실상 무제한으로 허용한다.
	public static final int SUBMISSION_LIMIT = Integer.MAX_VALUE;
	public static final Duration SESSION_TTL = Duration.ofDays(7);

	private final GuestSessionRepository guestSessionRepository;

	public GuestSessionService(GuestSessionRepository guestSessionRepository) {
		this.guestSessionRepository = guestSessionRepository;
	}

	public GuestSubmissionQuota consumeSubmission(String requestedGuestSessionId) {
		Instant now = Instant.now();
		GuestSession session = guestSessionRepository.findById(requestedGuestSessionId)
			.filter(existing -> !existing.isExpired(now))
			.orElseGet(() -> guestSessionRepository.save(UUID.randomUUID().toString(), now.plus(SESSION_TTL)));
		boolean newSession = !session.id().equals(requestedGuestSessionId);

		OptionalInt submissionCount = guestSessionRepository.incrementSubmissionCount(
			session.id(),
			SUBMISSION_LIMIT
		);
		if (submissionCount.isEmpty()) {
			throw new GuestSubmissionLimitExceededException();
		}
		return new GuestSubmissionQuota(
			session.id(),
			SUBMISSION_LIMIT - submissionCount.getAsInt(),
			newSession
		);
	}
}
