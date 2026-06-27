package com.psplatform.backend.judge;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.submission.Submission;

@Service
public class JudgeJobService {

	private final JudgeJobRepository judgeJobRepository;

	public JudgeJobService(JudgeJobRepository judgeJobRepository) {
		this.judgeJobRepository = judgeJobRepository;
	}

	@Transactional
	public Optional<JudgeJobAssignment> findNextAndMarkRunning() {
		return judgeJobRepository.findNextAndMarkRunning();
	}

	@Transactional
	public Submission complete(long jobId, JudgeJobCompleteRequest request) {
		return judgeJobRepository.complete(jobId, request.verdict());
	}
}
