package com.psplatform.backend.report;

import java.time.LocalDateTime;

public class ProblemReport {

	private final Long id;
	private final long problemId;
	private final String problemTitle;
	private final long reporterUserId;
	private final String reporterUsername;
	private final ProblemReportType type;
	private final String message;
	private final ProblemReportStatus status;
	private final LocalDateTime createdAt;
	private final LocalDateTime reviewedAt;
	private final String adminNote;

	public ProblemReport(
		Long id,
		long problemId,
		String problemTitle,
		long reporterUserId,
		String reporterUsername,
		ProblemReportType type,
		String message,
		ProblemReportStatus status,
		LocalDateTime createdAt,
		LocalDateTime reviewedAt,
		String adminNote
	) {
		this.id = id;
		this.problemId = problemId;
		this.problemTitle = problemTitle;
		this.reporterUserId = reporterUserId;
		this.reporterUsername = reporterUsername;
		this.type = type;
		this.message = message;
		this.status = status;
		this.createdAt = createdAt;
		this.reviewedAt = reviewedAt;
		this.adminNote = adminNote;
	}

	public Long getId() {
		return id;
	}

	public long getProblemId() {
		return problemId;
	}

	public String getProblemTitle() {
		return problemTitle;
	}

	public long getReporterUserId() {
		return reporterUserId;
	}

	public String getReporterUsername() {
		return reporterUsername;
	}

	public ProblemReportType getType() {
		return type;
	}

	public String getMessage() {
		return message;
	}

	public ProblemReportStatus getStatus() {
		return status;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getReviewedAt() {
		return reviewedAt;
	}

	public String getAdminNote() {
		return adminNote;
	}
}
