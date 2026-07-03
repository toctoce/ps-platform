package com.psplatform.backend.report;

public record ProblemReportCreateRequest(
	ProblemReportType type,
	String message
) {
}
