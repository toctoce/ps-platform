package com.psplatform.backend.problem;

import java.util.List;

public record MockTestResponse(
	List<ProblemResponse> problems,
	List<ProblemDifficulty> unavailableDifficulties
) {
}
