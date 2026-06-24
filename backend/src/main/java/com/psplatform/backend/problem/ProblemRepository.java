package com.psplatform.backend.problem;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository {

	Problem save(ProblemCreateRequest request);

	List<Problem> findAll();

	Optional<Problem> findById(long id);

	void deleteAll();
}
