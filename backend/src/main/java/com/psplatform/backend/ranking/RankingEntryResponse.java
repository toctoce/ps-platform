package com.psplatform.backend.ranking;

public record RankingEntryResponse(
	int rank,
	String username,
	int level,
	long currentLevelExperience,
	int requiredExperience
) {
}
