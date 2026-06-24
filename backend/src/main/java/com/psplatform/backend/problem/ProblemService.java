package com.psplatform.backend.problem;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class ProblemService {

	private final ProblemRepository problemRepository;

	public ProblemService(ProblemRepository problemRepository) {
		this.problemRepository = problemRepository;
	}

	public Problem create(ProblemCreateRequest request) {
		return problemRepository.save(request);
	}

	public List<Problem> findAll() {
		return problemRepository.findAll();
	}

	public Problem findById(long id) {
		return problemRepository.findById(id)
			.orElseThrow(() -> new ProblemNotFoundException(id));
	}
}
