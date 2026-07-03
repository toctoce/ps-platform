package com.psplatform.backend.submission;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;

public interface SubmissionRepository {

	Submission save(long problemId, Long userId, SubmissionCreateRequest request);

	default Submission save(long problemId, SubmissionCreateRequest request) {
		return save(problemId, null, request);
	}

	Optional<Submission> findById(long id);

	List<Submission> findByProblemId(long problemId);

	long countCreatedOn(LocalDate date);

	void deleteAll();
}
