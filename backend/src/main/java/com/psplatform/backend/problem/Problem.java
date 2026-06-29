package com.psplatform.backend.problem;

import java.time.LocalDateTime;
import java.util.List;

public class Problem {

	private final Long id;
	private final String title;
	private final String description;
	private final String inputDescription;
	private final String outputDescription;
	private final int timeLimitMs;
	private final int memoryLimitMb;
	private final ProblemDifficulty difficulty;
	private final LocalDateTime createdAt;
	private final List<ProblemCategory> categories;

	public Problem(
		Long id,
		String title,
		String description,
		String inputDescription,
		String outputDescription,
		int timeLimitMs,
		int memoryLimitMb,
		ProblemDifficulty difficulty,
		LocalDateTime createdAt
	) {
		this(
			id,
			title,
			description,
			inputDescription,
			outputDescription,
			timeLimitMs,
			memoryLimitMb,
			difficulty,
			createdAt,
			List.of()
		);
	}

	public Problem(
		Long id,
		String title,
	String description,
	String inputDescription,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb,
		ProblemDifficulty difficulty,
		LocalDateTime createdAt,
		List<ProblemCategory> categories
	) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.inputDescription = inputDescription;
		this.outputDescription = outputDescription;
		this.timeLimitMs = timeLimitMs;
		this.memoryLimitMb = memoryLimitMb;
		this.difficulty = difficulty;
		this.createdAt = createdAt;
		this.categories = categories == null ? List.of() : List.copyOf(categories);
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getInputDescription() {
		return inputDescription;
	}

	public String getOutputDescription() {
		return outputDescription;
	}

	public int getTimeLimitMs() {
		return timeLimitMs;
	}

	public int getMemoryLimitMb() {
		return memoryLimitMb;
	}

	public ProblemDifficulty getDifficulty() {
		return difficulty;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public List<ProblemCategory> getCategories() {
		return categories;
	}

	public Problem withCategories(List<ProblemCategory> categories) {
		return new Problem(
			id,
			title,
			description,
			inputDescription,
			outputDescription,
			timeLimitMs,
			memoryLimitMb,
			difficulty,
			createdAt,
			categories
		);
	}
}
