package com.psplatform.backend.problem;

public enum ProblemDifficulty {
	EASY,
	NORMAL,
	HARD,
	VERY_HARD;

	public String getLabel() {
		return switch (this) {
			case EASY -> "쉬움";
			case NORMAL -> "보통";
			case HARD -> "어려움";
			case VERY_HARD -> "매우 어려움";
		};
	}

	public static ProblemDifficulty orDefault(ProblemDifficulty difficulty) {
		return difficulty == null ? NORMAL : difficulty;
	}
}
