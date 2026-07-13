package com.psplatform.backend.submission;

import java.time.Instant;

public record SubmissionResponse(
	Long id,
	Long problemId,
	Long userId,
	String username,
	String language,
	String sourceCode,
	SubmissionStatus status,
	SubmissionVerdict verdict,
	String judgeMessage,
	Integer executionTimeMs,
	Integer memoryMb,
	Instant createdAt,
	Instant judgedAt
) {

	public static SubmissionResponse from(Submission submission) {
		return new SubmissionResponse(
			submission.getId(),
			submission.getProblemId(),
			submission.getUserId(),
			submission.getUsername(),
			submission.getLanguage(),
			submission.getSourceCode(),
			submission.getStatus(),
			submission.getVerdict(),
			submission.getJudgeMessage(),
			submission.getExecutionTimeMs(),
			submission.getMemoryMb(),
			submission.getCreatedAt(),
			submission.getJudgedAt()
		);
	}
}
