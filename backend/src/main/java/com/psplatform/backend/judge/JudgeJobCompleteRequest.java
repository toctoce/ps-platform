package com.psplatform.backend.judge;

import com.psplatform.backend.submission.SubmissionVerdict;

public record JudgeJobCompleteRequest(
	SubmissionVerdict verdict,
	String judgeMessage
) {
}
