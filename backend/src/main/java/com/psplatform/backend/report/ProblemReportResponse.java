package com.psplatform.backend.report;

import java.time.LocalDateTime;

public record ProblemReportResponse(
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
	public static ProblemReportResponse from(ProblemReport report) {
		return new ProblemReportResponse(
			report.getId(),
			report.getProblemId(),
			report.getProblemTitle(),
			report.getReporterUserId(),
			report.getReporterUsername(),
			report.getType(),
			report.getMessage(),
			report.getStatus(),
			report.getCreatedAt(),
			report.getReviewedAt(),
			report.getAdminNote()
		);
	}
}
