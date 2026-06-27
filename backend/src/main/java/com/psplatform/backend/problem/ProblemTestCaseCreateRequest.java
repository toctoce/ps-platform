package com.psplatform.backend.problem;

public record ProblemTestCaseCreateRequest(
	String input,
	String expectedOutput
) {
}
