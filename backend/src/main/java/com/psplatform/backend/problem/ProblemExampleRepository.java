package com.psplatform.backend.problem;

import java.util.List;

public interface ProblemExampleRepository {

	ProblemExample save(long problemId, ProblemExampleCreateRequest example);

	void saveAll(long problemId, List<ProblemExampleCreateRequest> examples);

	List<ProblemExample> findByProblemId(long problemId);

	void deleteByProblemId(long problemId);
}
