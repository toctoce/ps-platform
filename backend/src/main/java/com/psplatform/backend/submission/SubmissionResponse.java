package com.psplatform.backend.submission;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
			toUtcInstant(submission.getCreatedAt()),
			toUtcInstant(submission.getJudgedAt())
		);
	}

	private static Instant toUtcInstant(LocalDateTime dateTime) {
		return dateTime == null ? null : dateTime.toInstant(ZoneOffset.UTC);
	}
}
