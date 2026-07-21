package com.psplatform.backend.experience;

public interface ExperienceAwardRepository {

	boolean award(long userId, long problemId, int experience);

	long sumByUserId(long userId);
}
