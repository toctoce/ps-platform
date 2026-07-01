package com.psplatform.backend.judge;

import java.util.List;

import com.psplatform.backend.submission.JudgeJobStatus;
import com.psplatform.backend.submission.SubmissionVerdict;

public record CodeRunResponse(
	Long jobId,
	Long problemId,
	String language,
	JudgeJobStatus status,
	SubmissionVerdict verdict,
	String judgeMessage,
	List<JudgeCaseResult> caseResults
) {
}
