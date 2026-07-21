package com.psplatform.backend.experience;

public record UserLevelProgress(
	int level,
	long totalExperience,
	long currentLevelExperience,
	int requiredExperience,
	int progressPercent
) {
}
