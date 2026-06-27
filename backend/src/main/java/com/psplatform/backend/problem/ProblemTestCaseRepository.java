package com.psplatform.backend.problem;

import java.util.List;

public interface ProblemTestCaseRepository {

	ProblemTestCase save(long problemId, ProblemTestCaseCreateRequest testCase);

	void saveAll(long problemId, List<ProblemTestCaseCreateRequest> testCases);

	List<ProblemTestCase> findByProblemId(long problemId);

	void deleteByProblemId(long problemId);
}
