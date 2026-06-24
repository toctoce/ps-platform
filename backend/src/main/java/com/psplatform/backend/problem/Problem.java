package com.psplatform.backend.problem;

import java.time.LocalDateTime;

public record Problem(
	Long id,
	String title,
	String description,
	String inputDescription,
	String outputDescription,
	int timeLimitMs,
	int memoryLimitMb,
	LocalDateTime createdAt
) {
}
