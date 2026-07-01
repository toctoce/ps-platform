package com.psplatform.backend.judge;

import java.util.List;

public record JudgeJobAssignment(
	Long jobId,
	JudgeJobKind jobKind,
	Long submissionId,
	Long problemId,
	String language,
	String sourceCode,
	int timeLimitMs,
	int memoryLimitMb,
	List<JudgeTestCase> testCases
) {
}
