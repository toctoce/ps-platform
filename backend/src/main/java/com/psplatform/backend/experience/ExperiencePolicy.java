package com.psplatform.backend.experience;

import org.springframework.stereotype.Component;

import com.psplatform.backend.problem.ProblemDifficulty;

@Component
public class ExperiencePolicy {

	public int experienceFor(ProblemDifficulty difficulty) {
		return switch (difficulty) {
			case EASY -> 10;
			case NORMAL -> 30;
			case HARD -> 60;
			case VERY_HARD -> 100;
		};
	}
}
