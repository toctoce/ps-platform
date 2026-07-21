package com.psplatform.backend.experience;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.psplatform.backend.problem.ProblemDifficulty;

class ExperiencePolicyTest {

	private final ExperiencePolicy experiencePolicy = new ExperiencePolicy();

	@Test
	void awardsExperienceByProblemDifficulty() {
		assertThat(experiencePolicy.experienceFor(ProblemDifficulty.EASY)).isEqualTo(10);
		assertThat(experiencePolicy.experienceFor(ProblemDifficulty.NORMAL)).isEqualTo(30);
		assertThat(experiencePolicy.experienceFor(ProblemDifficulty.HARD)).isEqualTo(60);
		assertThat(experiencePolicy.experienceFor(ProblemDifficulty.VERY_HARD)).isEqualTo(100);
	}
}
