package com.psplatform.backend.ranking;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.psplatform.backend.experience.ExperienceAwardRepository;
import com.psplatform.backend.experience.LevelPolicy;
import com.psplatform.backend.experience.UserExperienceRankingCandidate;
import com.psplatform.backend.experience.UserLevelProgress;

@Service
public class RankingService {
	private static final int RANKING_LIMIT = 10;

	private final ExperienceAwardRepository experienceAwardRepository;
	private final LevelPolicy levelPolicy;

	public RankingService(ExperienceAwardRepository experienceAwardRepository, LevelPolicy levelPolicy) {
		this.experienceAwardRepository = experienceAwardRepository;
		this.levelPolicy = levelPolicy;
	}

	public List<RankingEntryResponse> findTopRankings() {
		List<UserExperienceRankingCandidate> candidates = experienceAwardRepository.findTopUsers(RANKING_LIMIT);
		List<RankingEntryResponse> rankings = new ArrayList<>(candidates.size());
		long previousExperience = -1;
		int currentRank = 0;

		for (int index = 0; index < candidates.size(); index++) {
			UserExperienceRankingCandidate candidate = candidates.get(index);
			if (candidate.totalExperience() != previousExperience) {
				currentRank = index + 1;
				previousExperience = candidate.totalExperience();
			}
			UserLevelProgress progress = levelPolicy.calculate(candidate.totalExperience());
			rankings.add(new RankingEntryResponse(
				currentRank,
				candidate.username(),
				progress.level(),
				progress.currentLevelExperience(),
				progress.requiredExperience()
			));
		}
		return rankings;
	}
}
