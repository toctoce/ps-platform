package com.psplatform.backend.submission;

public class SubmissionNotFoundException extends RuntimeException {

	public SubmissionNotFoundException(long id) {
		super("Submission not found: " + id);
	}
}
