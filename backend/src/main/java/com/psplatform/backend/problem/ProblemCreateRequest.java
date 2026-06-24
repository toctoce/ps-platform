package com.psplatform.backend.problem;

public record ProblemCreateRequest(
	String title,
	String description,
	String inputDescription,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb
) {
}
