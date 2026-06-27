package com.psplatform.backend.judge;

public record JudgeJobAssignment(
	Long jobId,
	Long submissionId,
	Long problemId,
	String language,
	String sourceCode,
	int timeLimitMs,
	int memoryLimitMb
) {
}
