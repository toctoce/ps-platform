package com.psplatform.backend.problem;

public record ProblemTestCaseResponse(
	Long id,
	Long problemId,
	String input,
	String expectedOutput,
	int sortOrder
) {
	public static ProblemTestCaseResponse from(ProblemTestCase testCase) {
		return new ProblemTestCaseResponse(
			testCase.getId(),
			testCase.getProblemId(),
			testCase.getInput(),
			testCase.getExpectedOutput(),
			testCase.getSortOrder()
		);
	}
}
