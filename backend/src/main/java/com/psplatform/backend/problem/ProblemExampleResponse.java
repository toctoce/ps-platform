package com.psplatform.backend.problem;

public record ProblemExampleResponse(
	Long id,
	Long problemId,
	String input,
	String expectedOutput,
	String description,
	int sortOrder
) {
	public static ProblemExampleResponse from(ProblemExample example) {
		return new ProblemExampleResponse(
			example.getId(),
			example.getProblemId(),
			example.getInput(),
			example.getExpectedOutput(),
			example.getDescription(),
			example.getSortOrder()
		);
	}
}
