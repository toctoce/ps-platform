package com.psplatform.backend.problem;

import java.sql.PreparedStatement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProblemTestCaseRepository implements ProblemTestCaseRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<ProblemTestCase> rowMapper = (rs, rowNum) -> new ProblemTestCase(
		rs.getLong("id"),
		rs.getLong("problem_id"),
		rs.getString("input_data"),
		rs.getString("expected_output"),
		rs.getInt("sort_order")
	);

	public JdbcProblemTestCaseRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public ProblemTestCase save(long problemId, ProblemTestCaseCreateRequest testCase) {
		Integer nextSortOrder = jdbcTemplate.queryForObject(
			"""
			SELECT COALESCE(MAX(sort_order) + 1, 0)
			FROM problem_test_cases
			WHERE problem_id = ?
			""",
			Integer.class,
			problemId
		);

		long testCaseId = insert(problemId, testCase, nextSortOrder == null ? 0 : nextSortOrder);
		return findByProblemId(problemId).stream()
			.filter(savedTestCase -> savedTestCase.getId().equals(testCaseId))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Saved problem test case was not found"));
	}

	@Override
	public void saveAll(long problemId, List<ProblemTestCaseCreateRequest> testCases) {
		for (int i = 0; i < testCases.size(); i++) {
			insert(problemId, testCases.get(i), i);
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

	private long insert(long problemId, ProblemTestCaseCreateRequest testCase, int sortOrder) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
				"""
				INSERT INTO problem_test_cases (problem_id, input_data, expected_output, sort_order)
				VALUES (?, ?, ?, ?)
				""",
				new String[] {"id"}
			);
			ps.setLong(1, problemId);
			ps.setString(2, testCase.input());
			ps.setString(3, testCase.expectedOutput());
			ps.setInt(4, sortOrder);
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Problem test case ID was not generated");
		}

		return key.longValue();
	}
}
