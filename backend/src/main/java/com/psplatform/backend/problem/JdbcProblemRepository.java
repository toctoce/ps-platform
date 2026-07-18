package com.psplatform.backend.problem;

import java.sql.PreparedStatement;
import java.util.regex.Pattern;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProblemRepository implements ProblemRepository {
	private static final String ADMIN_ROLE = "ADMIN";

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<Problem> rowMapper = (rs, rowNum) -> new Problem(
		rs.getLong("id"),
		rs.getString("title"),
		rs.getString("description"),
		decodeEscapedCharacters(rs.getString("solution")),
		rs.getString("input_description"),
		rs.getString("input_constraints"),
		rs.getString("output_description"),
		rs.getInt("time_limit_ms"),
		rs.getInt("memory_limit_mb"),
		ProblemDifficulty.valueOf(rs.getString("difficulty")),
		rs.getTimestamp("created_at").toLocalDateTime()
	);

	public JdbcProblemRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	private static final Pattern MARKDOWN_HINT_PATTERN = Pattern.compile(
		"(?m)^(\\s{0,3}#{1,6}\\s+|\\s{0,3}>\\s+|\\s{0,3}\\d+\\.\\s+|\\s{0,3}[-*+]\\s+|```|\\|.*\\||`{3,}|\\*{2}|#{1,6}\\s+)",
		Pattern.MULTILINE
	);

	@Override
	public Problem save(ProblemCreateRequest request) {
		String sql = """
			INSERT INTO problems (
				title,
				description,
				solution,
				input_description,
				input_constraints,
				output_description,
				time_limit_ms,
				memory_limit_mb,
				difficulty
			)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		try {
			jdbcTemplate.update(connection -> {
				PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
				ps.setString(1, request.title());
				ps.setString(2, request.description());
				ps.setString(3, normalizeSolution(request.solution()));
				ps.setString(4, request.inputDescription());
				ps.setString(5, request.inputConstraints());
				ps.setString(6, request.outputDescription());
				ps.setInt(7, request.timeLimitMs());
				ps.setInt(8, request.memoryLimitMb());
				ps.setString(9, request.difficulty().name());
				return ps;
			}, keyHolder);
		} catch (DuplicateKeyException exception) {
			throw new DuplicateProblemTitleException(request.title(), exception);
		}

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
			SELECT id, title, description, solution, input_description, input_constraints, output_description,
			       time_limit_ms, memory_limit_mb, difficulty, created_at
			FROM problems
			WHERE archived_at IS NULL
			ORDER BY id DESC
			""",
			rowMapper
		);
	}

	@Override
	public Optional<Problem> findById(long id) {
		List<Problem> problems = jdbcTemplate.query(
			"""
			SELECT id, title, description, solution, input_description, input_constraints, output_description,
			       time_limit_ms, memory_limit_mb, difficulty, created_at
			FROM problems
			WHERE id = ? AND archived_at IS NULL
			""",
			rowMapper,
			id
		);
		return problems.stream().findFirst();
	}

	@Override
	public boolean existsByTitle(String title) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM problems WHERE title = ?",
			Integer.class,
			title
		);
		return count != null && count > 0;
	}

	@Override
	public List<Problem> findByCategory(String category) {
		if (category == null || category.isBlank()) {
			return findAll();
		}
		return jdbcTemplate.query(
			"""
			SELECT p.id, p.title, p.description, p.solution, p.input_description, p.input_constraints, p.output_description,
			       p.time_limit_ms, p.memory_limit_mb, p.difficulty, p.created_at
			FROM problems p
			INNER JOIN problem_category_links pcl ON p.id = pcl.problem_id
			INNER JOIN problem_categories pc ON pc.id = pcl.category_id
			WHERE pc.name = ? AND pc.active = TRUE AND p.archived_at IS NULL
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
			SELECT id, title, description, solution, input_description, input_constraints, output_description,
			       time_limit_ms, memory_limit_mb, difficulty, created_at
			FROM problems
			WHERE archived_at IS NULL
			ORDER BY RANDOM()
			LIMIT ?
			""",
			rowMapper,
			count
		);
	}

	@Override
	public List<Problem> findMockTestCandidates(ProblemDifficulty difficulty, Long userId) {
		String userExclusion = userId == null ? "" : """
			  AND NOT EXISTS (
				SELECT 1
				FROM submissions s
				JOIN users u ON u.id = s.user_id
				WHERE s.problem_id = p.id
				  AND s.user_id = ?
				  AND s.verdict = 'ACCEPTED'
				  AND u.role <> ?
			  )
			""";
		String sql = """
			SELECT p.id, p.title, p.description, p.solution, p.input_description, p.input_constraints,
			       p.output_description, p.time_limit_ms, p.memory_limit_mb, p.difficulty, p.created_at
			FROM problems p
			WHERE p.archived_at IS NULL
			  AND p.difficulty = ?
			%s
			ORDER BY p.id
			""".formatted(userExclusion);
		if (userId == null) {
			return jdbcTemplate.query(sql, rowMapper, difficulty.name());
		}
		return jdbcTemplate.query(sql, rowMapper, difficulty.name(), userId, ADMIN_ROLE);
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM judge_jobs");
		jdbcTemplate.update("DELETE FROM submissions");
		jdbcTemplate.update("DELETE FROM problem_examples");
		jdbcTemplate.update("DELETE FROM problem_test_cases");
		jdbcTemplate.update("DELETE FROM problem_category_links");
		jdbcTemplate.update("DELETE FROM problem_categories");
		jdbcTemplate.update("DELETE FROM problems");
	}

	private String normalizeSolution(String solution) {
		if (solution == null || solution.isBlank()) {
			return null;
		}
		String normalized = decodeEscapedCharacters(solution)
			.replace("\r\n", "\n")
			.trim();
		if (MARKDOWN_HINT_PATTERN.matcher(normalized).find()) {
			return normalized;
		}
		return "## 해설\n\n" + normalized.replace("\n", "\n\n");
	}

	private String decodeEscapedCharacters(String value) {
		if (value == null) {
			return null;
		}
		return value
			.replace("\\n", "\n")
			.replace("\\r", "\r")
			.replace("\\t", "\t");
	}
}
