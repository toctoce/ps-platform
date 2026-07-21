package com.psplatform.backend.experience;

import org.springframework.stereotype.Service;

import com.psplatform.backend.problem.ProblemDifficulty;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.submission.Submission;
import com.psplatform.backend.submission.SubmissionVerdict;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@Service
public class ExperienceService {
	private static final String ADMIN_ROLE = "ADMIN";

	private final ExperienceAwardRepository experienceAwardRepository;
	private final ExperiencePolicy experiencePolicy;
	private final LevelPolicy levelPolicy;
	private final ProblemRepository problemRepository;
	private final UserRepository userRepository;

	public ExperienceService(
		ExperienceAwardRepository experienceAwardRepository,
		ExperiencePolicy experiencePolicy,
		LevelPolicy levelPolicy,
		ProblemRepository problemRepository,
		UserRepository userRepository
	) {
		this.experienceAwardRepository = experienceAwardRepository;
		this.experiencePolicy = experiencePolicy;
		this.levelPolicy = levelPolicy;
		this.problemRepository = problemRepository;
		this.userRepository = userRepository;
	}

	public void awardForAcceptedSubmission(Submission submission) {
		if (submission.getVerdict() != SubmissionVerdict.ACCEPTED || submission.getUserId() == null) {
			return;
		}

		User user = userRepository.findById(submission.getUserId()).orElse(null);
		if (user == null || user.isDeleted() || ADMIN_ROLE.equals(user.getRole())) {
			return;
		}

		ProblemDifficulty difficulty = problemRepository.findDifficultyById(submission.getProblemId()).orElse(null);
		if (difficulty == null) {
			return;
		}

		experienceAwardRepository.award(
			user.getId(),
			submission.getProblemId(),
			experiencePolicy.experienceFor(difficulty)
		);
	}

	public UserLevelProgress findProgress(long userId) {
		return levelPolicy.calculate(experienceAwardRepository.sumByUserId(userId));
	}
}
