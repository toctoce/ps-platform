package com.psplatform.backend.submission;

import java.time.Instant;

import com.psplatform.backend.problem.ProblemDifficulty;

public record SolvedProblemRecord(
	long problemId,
	String title,
	ProblemDifficulty difficulty,
	Instant solvedAt
) {
}
