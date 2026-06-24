package com.psplatform.backend.submission;

import java.util.List;
import java.util.Optional;

public interface SubmissionRepository {

	Submission save(long problemId, SubmissionCreateRequest request);

	void createJudgeJob(long submissionId);

	Optional<Submission> findById(long id);

	List<Submission> findByProblemId(long problemId);

	int countJudgeJobsBySubmissionId(long submissionId);

	void deleteAll();
}
