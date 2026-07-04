package com.psplatform.backend.problem;

public record ProblemCategorySummary(
	Long id,
	String name,
	long problemCount
) {
}
