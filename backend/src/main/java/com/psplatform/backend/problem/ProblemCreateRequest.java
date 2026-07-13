package com.psplatform.backend.problem;

import java.util.List;
import java.util.stream.Collectors;

public record ProblemCreateRequest(
	String title,
	String description,
	String solution,
	String inputDescription,
	String inputConstraints,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb,
	ProblemDifficulty difficulty,
	List<String> categories,
	List<ProblemTestCaseCreateRequest> testCases,
	List<ProblemExampleCreateRequest> examples
) {

	public ProblemCreateRequest {
		if (title != null) {
			title = title.trim();
		}
		difficulty = ProblemDifficulty.orDefault(difficulty);
		if (inputConstraints != null && inputConstraints.isBlank()) {
			inputConstraints = null;
		}
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
		String inputConstraints,
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
			inputConstraints,
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
		this(
			title,
			description,
			solution,
			inputDescription,
			null,
			outputDescription,
			timeLimitMs,
			memoryLimitMb,
			difficulty,
			categories,
			testCases,
			examples
		);
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
			null,
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
			null,
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
