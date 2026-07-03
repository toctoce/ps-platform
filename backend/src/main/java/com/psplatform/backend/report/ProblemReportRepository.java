package com.psplatform.backend.report;

import java.util.List;
import java.util.Optional;

public interface ProblemReportRepository {

	ProblemReport save(long problemId, long reporterUserId, ProblemReportCreateRequest request);

	Optional<ProblemReport> findById(long id);

	List<ProblemReport> findAll();

	ProblemReport updateReview(long id, ProblemReportStatus status, String adminNote);

	void deleteAll();
}
