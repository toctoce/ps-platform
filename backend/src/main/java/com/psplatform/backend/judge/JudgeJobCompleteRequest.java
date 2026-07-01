package com.psplatform.backend.judge;

import java.util.List;

import com.psplatform.backend.submission.SubmissionVerdict;

public record JudgeJobCompleteRequest(
	SubmissionVerdict verdict,
	String judgeMessage,
	List<JudgeCaseResult> caseResults
) {
}
