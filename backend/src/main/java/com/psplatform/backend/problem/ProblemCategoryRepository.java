package com.psplatform.backend.problem;

import java.util.List;
import java.util.Map;

public interface ProblemCategoryRepository {

	void addLinks(long problemId, List<String> categoryNames);

	List<ProblemCategory> findAll();

	Map<Long, List<ProblemCategory>> findByProblemIds(List<Long> problemIds);

	void deleteAll();
}
