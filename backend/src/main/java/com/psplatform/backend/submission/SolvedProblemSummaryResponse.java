package com.psplatform.backend.submission;

import java.util.List;

import com.psplatform.backend.problem.ProblemDifficulty;

public record SolvedProblemSummaryResponse(
	long totalSolved,
	List<DifficultyCount> difficulties,
	List<CategoryCount> categories
) {
	public record DifficultyCount(ProblemDifficulty difficulty, long count) {
	}

	public record CategoryCount(String category, long count) {
	}
}
