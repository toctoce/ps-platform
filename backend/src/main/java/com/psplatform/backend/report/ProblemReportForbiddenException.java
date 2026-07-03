package com.psplatform.backend.report;

public class ProblemReportForbiddenException extends RuntimeException {

	public ProblemReportForbiddenException() {
		super("Admin role is required");
	}
}
