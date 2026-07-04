package com.psplatform.backend.submission;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SubmissionRepository {

	Submission save(long problemId, Long userId, SubmissionCreateRequest request);

	default Submission save(long problemId, SubmissionCreateRequest request) {
		return save(problemId, null, request);
	}

	Optional<Submission> findById(long id);

	List<Submission> findByProblemId(long problemId);

	List<Long> findAcceptedProblemIdsByUserId(long userId);

	long countCreatedOn(LocalDate date);

	long countDistinctUsersCreatedOn(LocalDate date);

	void deleteAll();
}
