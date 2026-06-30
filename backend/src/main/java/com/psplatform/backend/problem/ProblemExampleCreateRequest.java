package com.psplatform.backend.problem;

public record ProblemExampleCreateRequest(
	String input,
	String expectedOutput,
	String description
) {
}
