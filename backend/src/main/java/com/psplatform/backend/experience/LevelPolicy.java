package com.psplatform.backend.experience;

import org.springframework.stereotype.Component;

@Component
public class LevelPolicy {

	public static final int MAX_LEVEL = 100;

	public UserLevelProgress calculate(long totalExperience) {
		long normalizedExperience = Math.max(0, totalExperience);
		long remainingExperience = normalizedExperience;
		int level = 1;

		while (level < MAX_LEVEL) {
			int requiredExperience = requiredExperienceForNextLevel(level);
			if (remainingExperience < requiredExperience) {
				int progressPercent = (int) (remainingExperience * 100 / requiredExperience);
				return new UserLevelProgress(
					level,
					normalizedExperience,
					remainingExperience,
					requiredExperience,
					progressPercent
				);
			}
			remainingExperience -= requiredExperience;
			level++;
		}

		return new UserLevelProgress(MAX_LEVEL, normalizedExperience, 0, 0, 100);
	}

	public int requiredExperienceForNextLevel(int level) {
		if (level < 1 || level > MAX_LEVEL) {
			throw new IllegalArgumentException("Level must be between 1 and " + MAX_LEVEL);
		}
		if (level == MAX_LEVEL) {
			return 0;
		}
		if (level <= 20) {
			return 50 + 5 * (level - 1);
		}
		if (level <= 39) {
			return 150 + 5 * ((level - 21) / 2);
		}
		if (level <= 49) {
			return 200 + 10 * (level - 40);
		}
		if (level <= 59) {
			return 300 + 10 * (level - 50);
		}
		if (level <= 69) {
			return 420;
		}
		if (level <= 79) {
			return 480;
		}
		if (level <= 84) {
			return 540;
		}
		if (level <= 89) {
			return 600;
		}
		if (level <= 93) {
			return 660;
		}
		if (level <= 96) {
			return 720;
		}
		return 780;
	}
}
