package com.psplatform.backend.judge;

import com.psplatform.backend.submission.Submission;

public record JudgeJobCompleteResult(
	JudgeJobKind jobKind,
	Submission submission,
	CodeRunResponse codeRun
) {
	public static JudgeJobCompleteResult submission(Submission submission) {
		return new JudgeJobCompleteResult(JudgeJobKind.SUBMISSION, submission, null);
	}

	public static JudgeJobCompleteResult codeRun(CodeRunResponse codeRun) {
		return new JudgeJobCompleteResult(JudgeJobKind.CODE_RUN, null, codeRun);
	}
}
