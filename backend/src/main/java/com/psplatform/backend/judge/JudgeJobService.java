package com.psplatform.backend.judge;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	public JudgeJobCompleteResult complete(long jobId, JudgeJobCompleteRequest request) {
		return judgeJobRepository.complete(jobId, request.verdict(), request.judgeMessage(), request.caseResults());
	}

	@Transactional
	public JudgeJobRequeueStaleResponse requeueStale(JudgeJobRequeueStaleRequest request) {
		int requeuedCount = judgeJobRepository.requeueStale(request.staleAfterSeconds());
		return new JudgeJobRequeueStaleResponse(requeuedCount);
	}
}
