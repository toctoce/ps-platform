package com.psplatform.backend.submission;

import java.util.List;

public record SolvedProblemListResponse(
	List<SolvedProblemResponse> problems,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
