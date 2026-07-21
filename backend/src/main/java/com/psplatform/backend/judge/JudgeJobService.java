package com.psplatform.backend.judge;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.experience.ExperienceService;

@Service
public class JudgeJobService {

	private final JudgeJobRepository judgeJobRepository;
	private final ExperienceService experienceService;

	public JudgeJobService(JudgeJobRepository judgeJobRepository, ExperienceService experienceService) {
		this.judgeJobRepository = judgeJobRepository;
		this.experienceService = experienceService;
	}

	@Transactional
	public Optional<JudgeJobAssignment> findNextAndMarkRunning() {
		return judgeJobRepository.findNextAndMarkRunning();
	}

	@Transactional
	public JudgeJobCompleteResult complete(long jobId, JudgeJobCompleteRequest request) {
		JudgeJobCompleteResult result = judgeJobRepository.complete(
			jobId,
			request.verdict(),
			request.judgeMessage(),
			request.caseResults()
		);
		if (result.jobKind() == JudgeJobKind.SUBMISSION) {
			experienceService.awardForAcceptedSubmission(result.submission());
		}
		return result;
	}

	@Transactional
	public JudgeJobRequeueStaleResponse requeueStale(JudgeJobRequeueStaleRequest request) {
		int requeuedCount = judgeJobRepository.requeueStale(request.staleAfterSeconds());
		return new JudgeJobRequeueStaleResponse(requeuedCount);
	}
}
