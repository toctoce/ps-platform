package com.psplatform.backend.problem;

public record ProblemCategorySummaryResponse(
	Long id,
	String name,
	long problemCount
) {
	public static ProblemCategorySummaryResponse from(ProblemCategorySummary category) {
		return new ProblemCategorySummaryResponse(
			category.id(),
			category.name(),
			category.problemCount()
		);
	}
}
