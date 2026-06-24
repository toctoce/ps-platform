package com.psplatform.backend.submission;

public record SubmissionCreateRequest(
	String language,
	String sourceCode
) {
}
