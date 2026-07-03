package com.psplatform.backend.report;

public record ProblemReportReviewRequest(
	ProblemReportStatus status,
	String adminNote
) {
}
