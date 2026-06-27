package com.psplatform.backend.problem;

public class ProblemTestCase {

	private final Long id;
	private final Long problemId;
	private final String input;
	private final String expectedOutput;
	private final int sortOrder;

	public ProblemTestCase(Long id, Long problemId, String input, String expectedOutput, int sortOrder) {
		this.id = id;
		this.problemId = problemId;
		this.input = input;
		this.expectedOutput = expectedOutput;
		this.sortOrder = sortOrder;
	}

	public Long getId() {
		return id;
	}

	public Long getProblemId() {
		return problemId;
	}

	public String getInput() {
		return input;
	}

	public String getExpectedOutput() {
		return expectedOutput;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
