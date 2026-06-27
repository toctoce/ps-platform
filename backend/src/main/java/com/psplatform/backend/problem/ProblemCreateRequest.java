package com.psplatform.backend.problem;

import java.util.List;

public record ProblemCreateRequest(
	String title,
	String description,
	String inputDescription,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb,
	List<ProblemTestCaseCreateRequest> testCases
) {
}
