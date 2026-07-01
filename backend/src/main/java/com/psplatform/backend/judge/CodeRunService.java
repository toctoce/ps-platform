package com.psplatform.backend.judge;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemNotFoundException;
import com.psplatform.backend.problem.ProblemRepository;

@Service
public class CodeRunService {

	private final ProblemRepository problemRepository;
	private final JudgeJobRepository judgeJobRepository;

	public CodeRunService(ProblemRepository problemRepository, JudgeJobRepository judgeJobRepository) {
		this.problemRepository = problemRepository;
		this.judgeJobRepository = judgeJobRepository;
	}

	@Transactional
	public CodeRunResponse create(long problemId, CodeRunCreateRequest request) {
		Problem problem = problemRepository.findById(problemId)
			.orElseThrow(() -> new ProblemNotFoundException(problemId));
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
