package com.psplatform.backend.problem;

import java.time.LocalDateTime;

public class Problem {

	private final Long id;
	private final String title;
	private final String description;
	private final String inputDescription;
	private final String outputDescription;
	private final int timeLimitMs;
	private final int memoryLimitMb;
	private final LocalDateTime createdAt;

	public Problem(
		Long id,
		String title,
		String description,
		String inputDescription,
		String outputDescription,
		int timeLimitMs,
		int memoryLimitMb,
		LocalDateTime createdAt
	) {
		this.id = id;
		this.title = title;
		this.description = description;
		this.inputDescription = inputDescription;
		this.outputDescription = outputDescription;
		this.timeLimitMs = timeLimitMs;
		this.memoryLimitMb = memoryLimitMb;
		this.createdAt = createdAt;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
