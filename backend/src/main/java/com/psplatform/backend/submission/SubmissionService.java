package com.psplatform.backend.submission;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.problem.ProblemNotFoundException;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.judge.JudgeJobRepository;

@Service
public class SubmissionService {

	private final ProblemRepository problemRepository;
	private final SubmissionRepository submissionRepository;
	private final JudgeJobRepository judgeJobRepository;

	public SubmissionService(
		ProblemRepository problemRepository,
		SubmissionRepository submissionRepository,
		JudgeJobRepository judgeJobRepository
	) {
		this.problemRepository = problemRepository;
		this.submissionRepository = submissionRepository;
		this.judgeJobRepository = judgeJobRepository;
	}

	@Transactional
	public Submission create(long problemId, SubmissionCreateRequest request) {
		if (problemRepository.findById(problemId).isEmpty()) {
			throw new ProblemNotFoundException(problemId);
		}

		Submission submission = submissionRepository.save(problemId, request);
		judgeJobRepository.create(submission.getId());
		return submission;
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
}
