package com.psplatform.backend.experience;

import java.util.List;

public interface ExperienceAwardRepository {

	boolean award(long userId, long problemId, int experience);

	long sumByUserId(long userId);

	List<UserExperienceRankingCandidate> findTopUsers(int limit);
}
