package com.psplatform.backend.submission;

import java.time.LocalDateTime;

public record SubmissionResponse(
	Long id,
	Long problemId,
	String language,
	String sourceCode,
	SubmissionStatus status,
	SubmissionVerdict verdict,
	LocalDateTime createdAt,
	LocalDateTime judgedAt
) {

	public static SubmissionResponse from(Submission submission) {
		return new SubmissionResponse(
			submission.getId(),
			submission.getProblemId(),
			submission.getLanguage(),
			submission.getSourceCode(),
			submission.getStatus(),
			submission.getVerdict(),
			submission.getCreatedAt(),
			submission.getJudgedAt()
		);
	}
}
