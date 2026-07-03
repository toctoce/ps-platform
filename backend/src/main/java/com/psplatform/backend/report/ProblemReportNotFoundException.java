package com.psplatform.backend.report;

public class ProblemReportNotFoundException extends RuntimeException {

	public ProblemReportNotFoundException(long id) {
		super("Problem report not found: " + id);
	}
}
