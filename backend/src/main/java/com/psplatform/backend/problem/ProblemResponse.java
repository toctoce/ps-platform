package com.psplatform.backend.problem;

import java.time.LocalDateTime;
import java.util.List;

public record ProblemResponse(
	Long id,
	String title,
	String description,
	String inputDescription,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb,
	ProblemDifficulty difficulty,
	LocalDateTime createdAt,
	List<ProblemCategoryResponse> categories
) {
	public static ProblemResponse from(Problem problem) {
		return new ProblemResponse(
			problem.getId(),
			problem.getTitle(),
			problem.getDescription(),
			problem.getInputDescription(),
			problem.getOutputDescription(),
			problem.getTimeLimitMs(),
			problem.getMemoryLimitMb(),
			problem.getDifficulty(),
			problem.getCreatedAt(),
			problem.getCategories().stream()
				.map(ProblemCategoryResponse::from)
				.toList()
		);
	}
}
