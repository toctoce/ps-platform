package com.psplatform.backend.submission;

import java.time.Instant;
import java.util.List;

import com.psplatform.backend.problem.ProblemCategory;
import com.psplatform.backend.problem.ProblemDifficulty;

public record SolvedProblemResponse(
	long problemId,
	String title,
	ProblemDifficulty difficulty,
	List<String> categories,
	Instant solvedAt
) {
	public static SolvedProblemResponse from(SolvedProblemRecord problem, List<ProblemCategory> categories) {
		return new SolvedProblemResponse(
			problem.problemId(),
			problem.title(),
			problem.difficulty(),
			categories.stream().map(ProblemCategory::name).toList(),
			problem.solvedAt()
		);
	}
}
