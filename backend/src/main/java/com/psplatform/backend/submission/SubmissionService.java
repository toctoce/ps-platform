package com.psplatform.backend.submission;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.problem.ProblemNotFoundException;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.judge.JudgeJobRepository;
import com.psplatform.backend.ratelimit.RateLimiter;

@Service
public class SubmissionService {

	private static final Duration SUBMISSION_RATE_LIMIT_WINDOW = Duration.ofSeconds(1);

	private final ProblemRepository problemRepository;
	private final SubmissionRepository submissionRepository;
	private final JudgeJobRepository judgeJobRepository;
	private final RateLimiter rateLimiter;
	private final GuestSessionService guestSessionService;

	public SubmissionService(
		ProblemRepository problemRepository,
		SubmissionRepository submissionRepository,
		JudgeJobRepository judgeJobRepository,
		RateLimiter rateLimiter,
		GuestSessionService guestSessionService
	) {
		this.problemRepository = problemRepository;
		this.submissionRepository = submissionRepository;
		this.judgeJobRepository = judgeJobRepository;
		this.rateLimiter = rateLimiter;
		this.guestSessionService = guestSessionService;
	}

	@Transactional
	public SubmissionCreationResult createForRequester(
		long problemId,
		SubmissionCreateRequest request,
		Long userId,
		String requestedGuestSessionId
	) {
		if (problemRepository.findById(problemId).isEmpty()) {
			throw new ProblemNotFoundException(problemId);
		}

		GuestSubmissionQuota guestQuota = null;
		if (userId != null) {
			rateLimiter.consume("submit:user:" + userId, 1, SUBMISSION_RATE_LIMIT_WINDOW);
		} else {
			guestQuota = guestSessionService.consumeSubmission(requestedGuestSessionId);
			rateLimiter.consume(
				"submit:guest:" + guestQuota.guestSessionId(),
				1,
				SUBMISSION_RATE_LIMIT_WINDOW
			);
		}

		Submission submission = submissionRepository.save(
			problemId,
			userId,
			guestQuota == null ? null : guestQuota.guestSessionId(),
			request
		);
		judgeJobRepository.create(submission.getId());
		return new SubmissionCreationResult(submission, guestQuota);
	}

	@Transactional
	public Submission create(long problemId, SubmissionCreateRequest request, Long userId) {
		return createForRequester(problemId, request, userId, null).submission();
	}

	public Submission create(long problemId, SubmissionCreateRequest request) {
		return create(problemId, request, null);
	}

	public Submission findById(long id) {
		return submissionRepository.findById(id)
			.orElseThrow(() -> new SubmissionNotFoundException(id));
	}

	public List<Submission> findByProblemId(long problemId) {
		if (problemRepository.findById(problemId).isEmpty()) {
			throw new ProblemNotFoundException(problemId);
		}

		return submissionRepository.findByProblemId(problemId);
	}

	public List<Long> findAcceptedProblemIdsByUserId(long userId) {
		return submissionRepository.findAcceptedProblemIdsByUserId(userId);
	}

	public SubmissionStatsResponse todayStats() {
		LocalDate today = LocalDate.now();
		return new SubmissionStatsResponse(
			submissionRepository.countCreatedOn(today),
			submissionRepository.countDistinctUsersCreatedOn(today)
		);
	}
}
