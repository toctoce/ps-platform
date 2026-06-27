package com.psplatform.backend.problem;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class H2ProblemTestCaseRepository implements ProblemTestCaseRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<ProblemTestCase> rowMapper = (rs, rowNum) -> new ProblemTestCase(
		rs.getLong("id"),
		rs.getLong("problem_id"),
		rs.getString("input_data"),
		rs.getString("expected_output"),
		rs.getInt("sort_order")
	);

	public H2ProblemTestCaseRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void saveAll(long problemId, List<ProblemTestCaseCreateRequest> testCases) {
		for (int i = 0; i < testCases.size(); i++) {
			ProblemTestCaseCreateRequest testCase = testCases.get(i);
			jdbcTemplate.update(
				"""
				INSERT INTO problem_test_cases (problem_id, input_data, expected_output, sort_order)
				VALUES (?, ?, ?, ?)
				""",
				problemId,
				testCase.input(),
				testCase.expectedOutput(),
				i
			);
		}
	}

	@Override
	public List<ProblemTestCase> findByProblemId(long problemId) {
		return jdbcTemplate.query(
			"""
			SELECT id, problem_id, input_data, expected_output, sort_order
			FROM problem_test_cases
			WHERE problem_id = ?
			ORDER BY sort_order
			""",
			rowMapper,
			problemId
		);
	}

	@Override
	public void deleteByProblemId(long problemId) {
		jdbcTemplate.update("DELETE FROM problem_test_cases WHERE problem_id = ?", problemId);
	}
}
