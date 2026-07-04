package com.psplatform.backend.problem;

import java.sql.PreparedStatement;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProblemExampleRepository implements ProblemExampleRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<ProblemExample> rowMapper = (rs, rowNum) -> new ProblemExample(
		rs.getLong("id"),
		rs.getLong("problem_id"),
		rs.getString("input_data"),
		rs.getString("expected_output"),
		rs.getString("description"),
		rs.getInt("sort_order")
	);

	public JdbcProblemExampleRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public ProblemExample save(long problemId, ProblemExampleCreateRequest example) {
		Integer nextSortOrder = jdbcTemplate.queryForObject(
			"""
			SELECT COALESCE(MAX(sort_order) + 1, 0)
			FROM problem_examples
			WHERE problem_id = ?
			""",
			Integer.class,
			problemId
		);

		long exampleId = insert(problemId, example, nextSortOrder == null ? 0 : nextSortOrder);
		return findByProblemId(problemId).stream()
			.filter(savedExample -> savedExample.getId().equals(exampleId))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Saved problem example was not found"));
	}

	@Override
	public void saveAll(long problemId, List<ProblemExampleCreateRequest> examples) {
		for (int i = 0; i < examples.size(); i++) {
			insert(problemId, examples.get(i), i);
		}
	}

	@Override
	public List<ProblemExample> findByProblemId(long problemId) {
		return jdbcTemplate.query(
			"""
			SELECT id, problem_id, input_data, expected_output, description, sort_order
			FROM problem_examples
			WHERE problem_id = ?
			ORDER BY sort_order
			""",
			rowMapper,
			problemId
		);
	}

	@Override
	public void deleteByProblemId(long problemId) {
		jdbcTemplate.update("DELETE FROM problem_examples WHERE problem_id = ?", problemId);
	}

	private long insert(long problemId, ProblemExampleCreateRequest example, int sortOrder) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
				"""
				INSERT INTO problem_examples (problem_id, input_data, expected_output, description, sort_order)
				VALUES (?, ?, ?, ?, ?)
				""",
				new String[] {"id"}
			);
			ps.setLong(1, problemId);
			ps.setString(2, example.input());
			ps.setString(3, example.expectedOutput());
			ps.setString(4, normalizeDescription(example.description()));
			ps.setInt(5, sortOrder);
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Problem example ID was not generated");
		}

		return key.longValue();
	}

	private String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.trim();
	}
}
