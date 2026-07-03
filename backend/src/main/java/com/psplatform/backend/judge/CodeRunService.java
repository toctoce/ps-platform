package com.psplatform.backend.judge;

import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemNotFoundException;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.ratelimit.RateLimiter;

@Service
public class CodeRunService {

	private static final Duration CODE_RUN_RATE_LIMIT_WINDOW = Duration.ofSeconds(1);

	private final ProblemRepository problemRepository;
	private final JudgeJobRepository judgeJobRepository;
	private final RateLimiter rateLimiter;

	public CodeRunService(
		ProblemRepository problemRepository,
		JudgeJobRepository judgeJobRepository,
		RateLimiter rateLimiter
	) {
		this.problemRepository = problemRepository;
		this.judgeJobRepository = judgeJobRepository;
		this.rateLimiter = rateLimiter;
	}

	@Transactional
	public CodeRunResponse create(long problemId, CodeRunCreateRequest request, long userId) {
		Problem problem = problemRepository.findById(problemId)
			.orElseThrow(() -> new ProblemNotFoundException(problemId));
		rateLimiter.consume("run:user:" + userId, 1, CODE_RUN_RATE_LIMIT_WINDOW);
		return judgeJobRepository.createCodeRun(
			problemId,
			problem.getTimeLimitMs(),
			problem.getMemoryLimitMb(),
			request
		);
	}

	public CodeRunResponse findByJobId(long jobId) {
		return judgeJobRepository.findCodeRunByJobId(jobId)
			.orElseThrow(() -> new JudgeJobNotFoundException(jobId));
	}
}
