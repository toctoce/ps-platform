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
			problem.id(),
			problem.title(),
			problem.description(),
			problem.inputDescription(),
			problem.outputDescription(),
			problem.timeLimitMs(),
			problem.memoryLimitMb(),
			problem.createdAt()
		);
	}
}
