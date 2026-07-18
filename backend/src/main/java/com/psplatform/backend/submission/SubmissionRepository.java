package com.psplatform.backend.submission;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.psplatform.backend.problem.ProblemDifficulty;

public interface SubmissionRepository {

	Submission save(long problemId, Long userId, String guestSessionId, SubmissionCreateRequest request);

	default Submission save(long problemId, Long userId, SubmissionCreateRequest request) {
		return save(problemId, userId, null, request);
	}

	default Submission save(long problemId, SubmissionCreateRequest request) {
		return save(problemId, null, null, request);
	}

	Optional<Submission> findById(long id);

	List<Submission> findByProblemId(long problemId);

	List<Long> findAcceptedProblemIdsByUserId(long userId);

	List<SolvedProblemRecord> findSolvedProblems(long userId, SolvedProblemSearch search);

	long countSolvedProblems(long userId, SolvedProblemSearch search);

	Map<ProblemDifficulty, Long> countSolvedProblemsByDifficulty(long userId);

	Map<String, Long> countSolvedProblemsByCategory(long userId);

	long countCreatedOn(LocalDate date);

	long countDistinctUsersCreatedOn(LocalDate date);

	void deleteAll();
}
