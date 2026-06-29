package com.psplatform.backend.problem;

public record ProblemCategoryResponse(
	Long id,
	String name
) {
	public static ProblemCategoryResponse from(ProblemCategory category) {
		return new ProblemCategoryResponse(category.id(), category.name());
	}
}
