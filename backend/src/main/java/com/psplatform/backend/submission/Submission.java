package com.psplatform.backend.submission;

import java.time.LocalDateTime;

public class Submission {

	private final Long id;
	private final Long problemId;
	private final String language;
	private final String sourceCode;
	private final SubmissionStatus status;
	private final SubmissionVerdict verdict;
	private final LocalDateTime createdAt;
	private final LocalDateTime judgedAt;

	public Submission(
		Long id,
		Long problemId,
		String language,
		String sourceCode,
		SubmissionStatus status,
		SubmissionVerdict verdict,
		LocalDateTime createdAt,
		LocalDateTime judgedAt
	) {
		this.id = id;
		this.problemId = problemId;
		this.language = language;
		this.sourceCode = sourceCode;
		this.status = status;
		this.verdict = verdict;
		this.createdAt = createdAt;
		this.judgedAt = judgedAt;
	}

	public Long getId() {
		return id;
	}

	public Long getProblemId() {
		return problemId;
	}

	public String getLanguage() {
		return language;
	}

	public String getSourceCode() {
		return sourceCode;
	}

	public SubmissionStatus getStatus() {
		return status;
	}

	public SubmissionVerdict getVerdict() {
		return verdict;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getJudgedAt() {
		return judgedAt;
	}
}
