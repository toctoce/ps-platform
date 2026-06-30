package com.psplatform.backend.problem;

import java.util.List;
import java.util.stream.Collectors;

public record ProblemCreateRequest(
	String title,
	String description,
	String solution,
	String inputDescription,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb,
	ProblemDifficulty difficulty,
	List<String> categories,
	List<ProblemTestCaseCreateRequest> testCases,
	List<ProblemExampleCreateRequest> examples
) {

	public ProblemCreateRequest {
		difficulty = ProblemDifficulty.orDefault(difficulty);
		if (categories == null) {
			categories = List.of();
		} else {
			categories = categories.stream()
				.map(category -> category == null ? "" : category.trim())
				.filter(category -> !category.isEmpty())
				.distinct()
				.collect(Collectors.toList());
		}
	}

	public ProblemCreateRequest(
		String title,
		String description,
		String solution,
		String inputDescription,
		String outputDescription,
		int timeLimitMs,
		int memoryLimitMb,
		ProblemDifficulty difficulty,
		List<String> categories,
		List<ProblemTestCaseCreateRequest> testCases
	) {
		this(
			title,
			description,
			solution,
			inputDescription,
			outputDescription,
			timeLimitMs,
			memoryLimitMb,
			difficulty,
			categories,
			testCases,
			List.of()
		);
	}

	public ProblemCreateRequest(
		String title,
		String description,
		String inputDescription,
		String outputDescription,
		int timeLimitMs,
		int memoryLimitMb,
		ProblemDifficulty difficulty,
		List<String> categories,
		List<ProblemTestCaseCreateRequest> testCases
	) {
		this(
			title,
			description,
			null,
			inputDescription,
			outputDescription,
			timeLimitMs,
			memoryLimitMb,
			difficulty,
			categories,
			testCases,
			List.of()
		);
	}
}
