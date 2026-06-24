package com.psplatform.backend.problem;

import java.time.LocalDateTime;

public record ProblemResponse(
	Long id,
	String title,
	String description,
	String inputDescription,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb,
	LocalDateTime createdAt
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
			problem.getCreatedAt()
		);
	}
}
