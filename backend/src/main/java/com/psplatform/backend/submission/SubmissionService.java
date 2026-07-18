package com.psplatform.backend.submission;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.problem.ProblemNotFoundException;
import com.psplatform.backend.problem.ProblemCategory;
import com.psplatform.backend.problem.ProblemCategoryRepository;
import com.psplatform.backend.problem.ProblemDifficulty;
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
	private final ProblemCategoryRepository problemCategoryRepository;

	public SubmissionService(
		ProblemRepository problemRepository,
		SubmissionRepository submissionRepository,
		JudgeJobRepository judgeJobRepository,
		RateLimiter rateLimiter,
		GuestSessionService guestSessionService,
		ProblemCategoryRepository problemCategoryRepository
	) {
		this.problemRepository = problemRepository;
		this.submissionRepository = submissionRepository;
		this.judgeJobRepository = judgeJobRepository;
		this.rateLimiter = rateLimiter;
		this.guestSessionService = guestSessionService;
		this.problemCategoryRepository = problemCategoryRepository;
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

	public SolvedProblemSummaryResponse findSolvedProblemSummary(long userId) {
		Map<ProblemDifficulty, Long> difficultyCounts =
			submissionRepository.countSolvedProblemsByDifficulty(userId);
		List<SolvedProblemSummaryResponse.DifficultyCount> difficulties =
			java.util.Arrays.stream(ProblemDifficulty.values())
				.map(difficulty -> new SolvedProblemSummaryResponse.DifficultyCount(
					difficulty,
					difficultyCounts.getOrDefault(difficulty, 0L)
				))
				.toList();
		List<SolvedProblemSummaryResponse.CategoryCount> categories =
			submissionRepository.countSolvedProblemsByCategory(userId)
				.entrySet()
				.stream()
				.map(entry -> new SolvedProblemSummaryResponse.CategoryCount(entry.getKey(), entry.getValue()))
				.toList();
		long totalSolved = difficulties.stream()
			.mapToLong(SolvedProblemSummaryResponse.DifficultyCount::count)
			.sum();
		return new SolvedProblemSummaryResponse(totalSolved, difficulties, categories);
	}

	public SolvedProblemListResponse findSolvedProblems(long userId, SolvedProblemSearch search) {
		List<SolvedProblemRecord> solvedProblems = submissionRepository.findSolvedProblems(userId, search);
		Map<Long, List<ProblemCategory>> categories = problemCategoryRepository.findByProblemIds(
			solvedProblems.stream().map(SolvedProblemRecord::problemId).toList()
		);
		List<SolvedProblemResponse> responses = solvedProblems.stream()
			.map(problem -> SolvedProblemResponse.from(
				problem,
				categories.getOrDefault(problem.problemId(), List.of())
			))
			.toList();
		long totalElements = submissionRepository.countSolvedProblems(userId, search);
		int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + search.size() - 1) / search.size());
		return new SolvedProblemListResponse(
			responses,
			search.page(),
			search.size(),
			totalElements,
			totalPages
		);
	}

	public SubmissionStatsResponse todayStats() {
		LocalDate today = LocalDate.now();
		return new SubmissionStatsResponse(
			submissionRepository.countCreatedOn(today),
			submissionRepository.countDistinctUsersCreatedOn(today)
		);
	}
}
