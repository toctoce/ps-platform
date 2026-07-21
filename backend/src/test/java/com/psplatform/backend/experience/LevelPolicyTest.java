package com.psplatform.backend.experience;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class LevelPolicyTest {

	private final LevelPolicy levelPolicy = new LevelPolicy();

	@Test
	void startsAtLevelOneWithNoExperience() {
		UserLevelProgress progress = levelPolicy.calculate(0);

		assertThat(progress.level()).isEqualTo(1);
		assertThat(progress.totalExperience()).isZero();
		assertThat(progress.currentLevelExperience()).isZero();
		assertThat(progress.requiredExperience()).isEqualTo(50);
		assertThat(progress.progressPercent()).isZero();
	}

	@Test
	void levelsUpAtTheExactThreshold() {
		UserLevelProgress progress = levelPolicy.calculate(50);

		assertThat(progress.level()).isEqualTo(2);
		assertThat(progress.currentLevelExperience()).isZero();
		assertThat(progress.requiredExperience()).isEqualTo(55);
	}

	@Test
	void usesFivePointUnitsForEarlyLevelThresholds() {
		assertThat(levelPolicy.requiredExperienceForNextLevel(1)).isEqualTo(50);
		assertThat(levelPolicy.requiredExperienceForNextLevel(2)).isEqualTo(55);
		assertThat(levelPolicy.requiredExperienceForNextLevel(20)).isEqualTo(145);
		assertThat(levelPolicy.requiredExperienceForNextLevel(21)).isEqualTo(150);
		assertThat(levelPolicy.requiredExperienceForNextLevel(39)).isEqualTo(195);
		assertThat(levelPolicy.requiredExperienceForNextLevel(40)).isEqualTo(200);
		assertThat(levelPolicy.requiredExperienceForNextLevel(59)).isEqualTo(390);
		assertThat(levelPolicy.requiredExperienceForNextLevel(60)).isEqualTo(420);
	}

	@Test
	void usesHardProblemCountsForLaterLevelThresholds() {
		assertThat(levelPolicy.requiredExperienceForNextLevel(60)).isEqualTo(60 * 7);
		assertThat(levelPolicy.requiredExperienceForNextLevel(70)).isEqualTo(60 * 8);
		assertThat(levelPolicy.requiredExperienceForNextLevel(80)).isEqualTo(60 * 9);
		assertThat(levelPolicy.requiredExperienceForNextLevel(85)).isEqualTo(60 * 10);
		assertThat(levelPolicy.requiredExperienceForNextLevel(90)).isEqualTo(60 * 11);
		assertThat(levelPolicy.requiredExperienceForNextLevel(94)).isEqualTo(60 * 12);
		assertThat(levelPolicy.requiredExperienceForNextLevel(97)).isEqualTo(60 * 13);
	}

	@Test
	void reachesMaximumLevelAtTheDesignedTotalExperience() {
		long maximumLevelExperience = 0;
		for (int level = 1; level < LevelPolicy.MAX_LEVEL; level++) {
			maximumLevelExperience += levelPolicy.requiredExperienceForNextLevel(level);
		}

		assertThat(maximumLevelExperience).isEqualTo(32_945);
		assertThat(levelPolicy.calculate(maximumLevelExperience - 1).level()).isEqualTo(99);
		assertThat(levelPolicy.calculate(maximumLevelExperience).level()).isEqualTo(100);
		assertThat(levelPolicy.calculate(maximumLevelExperience).progressPercent()).isEqualTo(100);
	}

	@Test
	void rejectsLevelsOutsideTheSupportedRange() {
		assertThatThrownBy(() -> levelPolicy.requiredExperienceForNextLevel(0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> levelPolicy.requiredExperienceForNextLevel(101))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
