package com.psplatform.backend.submission;

import com.psplatform.backend.problem.ProblemDifficulty;

public record SolvedProblemSearch(
	String query,
	ProblemDifficulty difficulty,
	String category,
	Sort sort,
	int page,
	int size
) {
	public enum Sort {
		PROBLEM_ID_ASC,
		PROBLEM_ID_DESC,
		SOLVED_AT_ASC,
		SOLVED_AT_DESC
	}

	public int offset() {
		return page * size;
	}
}
