package com.psplatform.backend.judge;

import com.psplatform.backend.submission.SubmissionVerdict;

public record JudgeCaseResult(
	int index,
	SubmissionVerdict verdict,
	String stdout,
	String expectedOutput,
	String message
) {
}
