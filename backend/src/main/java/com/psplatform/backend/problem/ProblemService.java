package com.psplatform.backend.problem;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemService {

	private final ProblemRepository problemRepository;
	private final ProblemTestCaseRepository problemTestCaseRepository;

	public ProblemService(ProblemRepository problemRepository, ProblemTestCaseRepository problemTestCaseRepository) {
		this.problemRepository = problemRepository;
		this.problemTestCaseRepository = problemTestCaseRepository;
	}

	@Transactional
	public Problem create(ProblemCreateRequest request) {
		Problem problem = problemRepository.save(request);
		if (request.testCases() != null) {
			problemTestCaseRepository.saveAll(problem.getId(), request.testCases());
		}
		return problem;
	}

	public List<Problem> findAll() {
		return problemRepository.findAll();
	}

	public Problem findById(long id) {
		return problemRepository.findById(id)
			.orElseThrow(() -> new ProblemNotFoundException(id));
	}
}
