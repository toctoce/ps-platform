package com.psplatform.backend.problem;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class H2ProblemRepository implements ProblemRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<Problem> rowMapper = (rs, rowNum) -> new Problem(
		rs.getLong("id"),
		rs.getString("title"),
		rs.getString("description"),
		rs.getString("input_description"),
		rs.getString("output_description"),
		rs.getInt("time_limit_ms"),
		rs.getInt("memory_limit_mb"),
		ProblemDifficulty.valueOf(rs.getString("difficulty")),
		rs.getTimestamp("created_at").toLocalDateTime()
	);

	public H2ProblemRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Problem save(ProblemCreateRequest request) {
		String sql = """
			INSERT INTO problems (
				title,
				description,
				input_description,
				output_description,
				time_limit_ms,
				memory_limit_mb,
				difficulty
			)
			VALUES (?, ?, ?, ?, ?, ?, ?)
			""";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
			ps.setString(1, request.title());
			ps.setString(2, request.description());
			ps.setString(3, request.inputDescription());
			ps.setString(4, request.outputDescription());
			ps.setInt(5, request.timeLimitMs());
			ps.setInt(6, request.memoryLimitMb());
			ps.setString(7, request.difficulty().name());
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Problem ID was not generated");
		}

		return findById(key.longValue())
			.orElseThrow(() -> new IllegalStateException("Saved problem was not found"));
	}

	@Override
	public List<Problem> findAll() {
		return jdbcTemplate.query(
			"""
			SELECT id, title, description, input_description, output_description,
			       time_limit_ms, memory_limit_mb, difficulty, created_at
			FROM problems
			ORDER BY id DESC
			""",
			rowMapper
		);
	}

	@Override
	public Optional<Problem> findById(long id) {
		List<Problem> problems = jdbcTemplate.query(
			"""
			SELECT id, title, description, input_description, output_description,
			       time_limit_ms, memory_limit_mb, difficulty, created_at
			FROM problems
			WHERE id = ?
			""",
			rowMapper,
			id
		);
		return problems.stream().findFirst();
	}

	@Override
	public List<Problem> findByCategory(String category) {
		if (category == null || category.isBlank()) {
			return findAll();
		}
		return jdbcTemplate.query(
			"""
			SELECT p.id, p.title, p.description, p.input_description, p.output_description,
			       p.time_limit_ms, p.memory_limit_mb, p.difficulty, p.created_at
			FROM problems p
			INNER JOIN problem_category_links pcl ON p.id = pcl.problem_id
			INNER JOIN problem_categories pc ON pc.id = pcl.category_id
			WHERE pc.name = ?
			ORDER BY p.id DESC
			""",
			rowMapper,
			category
		);
	}

	@Override
	public List<Problem> findRandom(int count) {
		if (count <= 0) {
			return List.of();
		}
		return jdbcTemplate.query(
			"""
			SELECT id, title, description, input_description, output_description,
			       time_limit_ms, memory_limit_mb, difficulty, created_at
			FROM problems
			ORDER BY RAND()
			LIMIT ?
			""",
			rowMapper,
			count
		);
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM judge_jobs");
		jdbcTemplate.update("DELETE FROM submissions");
		jdbcTemplate.update("DELETE FROM problem_test_cases");
		jdbcTemplate.update("DELETE FROM problem_category_links");
		jdbcTemplate.update("DELETE FROM problem_categories");
		jdbcTemplate.update("DELETE FROM problems");
	}
}
