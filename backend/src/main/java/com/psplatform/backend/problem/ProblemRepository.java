package com.psplatform.backend.problem;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository {

	Problem save(ProblemCreateRequest request);

	List<Problem> findAll();

	List<Problem> findByCategory(String category);

	List<Problem> findRandom(int count);

	List<Problem> findMockTestCandidates(ProblemDifficulty difficulty, Long userId);

	Optional<Problem> findById(long id);

	Optional<ProblemDifficulty> findDifficultyById(long id);

	boolean existsByTitle(String title);

	void deleteAll();
}
