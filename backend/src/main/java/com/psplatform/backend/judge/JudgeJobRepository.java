package com.psplatform.backend.judge;

import java.util.Optional;

import com.psplatform.backend.submission.SubmissionVerdict;

public interface JudgeJobRepository {

	void create(long submissionId);

	CodeRunResponse createCodeRun(long problemId, int timeLimitMs, int memoryLimitMb, CodeRunCreateRequest request);

	Optional<JudgeJobAssignment> findNextAndMarkRunning();

	JudgeJobCompleteResult complete(long jobId, SubmissionVerdict verdict, String judgeMessage, java.util.List<JudgeCaseResult> caseResults);

	Optional<CodeRunResponse> findCodeRunByJobId(long jobId);

	int requeueStale(long staleAfterSeconds);

	int countBySubmissionId(long submissionId);
}
