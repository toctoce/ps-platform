package com.psplatform.backend.problem;

public class ProblemExample {

	private final Long id;
	private final Long problemId;
	private final String input;
	private final String expectedOutput;
	private final String description;
	private final int sortOrder;

	public ProblemExample(
		Long id,
		Long problemId,
		String input,
		String expectedOutput,
		String description,
		int sortOrder
	) {
		this.id = id;
		this.problemId = problemId;
		this.input = input;
		this.expectedOutput = expectedOutput;
		this.description = description;
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

	public String getDescription() {
		return description;
	}

	public int getSortOrder() {
		return sortOrder;
	}
}
