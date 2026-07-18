package com.psplatform.backend.submission;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.psplatform.backend.problem.ProblemDifficulty;

@Repository
public class JdbcSubmissionRepository implements SubmissionRepository {
	private static final String ADMIN_ROLE = "ADMIN";

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<Submission> rowMapper = (rs, rowNum) -> {
		Timestamp judgedAt = rs.getTimestamp("judged_at");
		String verdict = rs.getString("verdict");
		Long userId = getLongOrNull(rs, "user_id");
		return new Submission(
			rs.getLong("id"),
			rs.getLong("problem_id"),
			userId,
			rs.getString("username"),
			rs.getString("language"),
			rs.getString("source_code"),
			SubmissionStatus.valueOf(rs.getString("status")),
			verdict == null ? null : SubmissionVerdict.valueOf(verdict),
			rs.getString("judge_message"),
			getIntegerOrNull(rs, "execution_time_ms"),
			getIntegerOrNull(rs, "memory_mb"),
			rs.getTimestamp("created_at").toInstant(),
			judgedAt == null ? null : judgedAt.toInstant()
		);
	};

	public JdbcSubmissionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Submission save(long problemId, Long userId, String guestSessionId, SubmissionCreateRequest request) {
		String sql = """
			INSERT INTO submissions (
				problem_id,
				user_id,
				guest_session_id,
				language,
				source_code,
				status
			)
			VALUES (?, ?, ?, ?, ?, ?)
			""";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
			ps.setLong(1, problemId);
			if (userId == null) {
				ps.setNull(2, java.sql.Types.BIGINT);
			} else {
				ps.setLong(2, userId);
			}
			ps.setString(3, guestSessionId);
			ps.setString(4, request.language());
			ps.setString(5, request.sourceCode());
			ps.setString(6, SubmissionStatus.PENDING.name());
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Submission ID was not generated");
		}

		return findById(key.longValue())
			.orElseThrow(() -> new IllegalStateException("Saved submission was not found"));
	}

	@Override
	public Optional<Submission> findById(long id) {
		List<Submission> submissions = jdbcTemplate.query(
			"""
			SELECT s.id, s.problem_id, s.user_id,
			       CASE WHEN u.deleted_at IS NOT NULL THEN '탈퇴한 사용자' ELSE u.username END AS username,
			       s.language, s.source_code, s.status,
			       s.verdict, s.judge_message, s.execution_time_ms, s.memory_mb,
			       s.created_at, s.judged_at
			FROM submissions s
			LEFT JOIN users u ON u.id = s.user_id
			WHERE s.id = ?
			""",
			rowMapper,
			id
		);
		return submissions.stream().findFirst();
	}

	@Override
	public List<Submission> findByProblemId(long problemId) {
		return jdbcTemplate.query(
			"""
			SELECT s.id, s.problem_id, s.user_id,
			       CASE WHEN u.deleted_at IS NOT NULL THEN '탈퇴한 사용자' ELSE u.username END AS username,
			       s.language, s.source_code, s.status,
			       s.verdict, s.judge_message, s.execution_time_ms, s.memory_mb,
			       s.created_at, s.judged_at
			FROM submissions s
			LEFT JOIN users u ON u.id = s.user_id
			WHERE s.problem_id = ?
			  AND (s.user_id IS NULL OR u.role <> ?)
			ORDER BY id DESC
			""",
			rowMapper,
			problemId,
			ADMIN_ROLE
		);
	}

	@Override
	public List<Long> findAcceptedProblemIdsByUserId(long userId) {
		return jdbcTemplate.queryForList(
			"""
			SELECT DISTINCT problem_id
			FROM submissions
			WHERE user_id = ?
			  AND verdict = ?
			ORDER BY problem_id
			""",
			Long.class,
			userId,
			SubmissionVerdict.ACCEPTED.name()
		);
	}

	@Override
	public List<SolvedProblemRecord> findSolvedProblems(long userId, SolvedProblemSearch search) {
		List<Object> arguments = new ArrayList<>();
		String filters = solvedProblemFilters(userId, search, arguments);
		arguments.add(search.size());
		arguments.add(search.offset());

		String sql = """
			WITH solved AS (
				SELECT s.problem_id, MAX(s.created_at) AS solved_at
				FROM submissions s
				JOIN users u ON u.id = s.user_id
				WHERE s.user_id = ?
				  AND s.verdict = ?
				  AND u.role <> ?
				GROUP BY s.problem_id
			)
			SELECT p.id AS problem_id, p.title, p.difficulty, solved.solved_at
			FROM solved
			JOIN problems p ON p.id = solved.problem_id
			WHERE p.archived_at IS NULL
			%s
			ORDER BY %s
			LIMIT ? OFFSET ?
			""".formatted(filters, solvedProblemOrder(search.sort()));

		return jdbcTemplate.query(
			sql,
			(rs, rowNum) -> new SolvedProblemRecord(
				rs.getLong("problem_id"),
				rs.getString("title"),
				ProblemDifficulty.valueOf(rs.getString("difficulty")),
				rs.getTimestamp("solved_at").toInstant()
			),
			arguments.toArray()
		);
	}

	@Override
	public long countSolvedProblems(long userId, SolvedProblemSearch search) {
		List<Object> arguments = new ArrayList<>();
		String filters = solvedProblemFilters(userId, search, arguments);
		String sql = """
			WITH solved AS (
				SELECT s.problem_id
				FROM submissions s
				JOIN users u ON u.id = s.user_id
				WHERE s.user_id = ?
				  AND s.verdict = ?
				  AND u.role <> ?
				GROUP BY s.problem_id
			)
			SELECT COUNT(*)
			FROM solved
			JOIN problems p ON p.id = solved.problem_id
			WHERE p.archived_at IS NULL
			%s
			""".formatted(filters);
		Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments.toArray());
		return count == null ? 0 : count;
	}

	@Override
	public Map<ProblemDifficulty, Long> countSolvedProblemsByDifficulty(long userId) {
		Map<ProblemDifficulty, Long> result = new LinkedHashMap<>();
		jdbcTemplate.query(
			"""
			WITH solved AS (
				SELECT DISTINCT s.problem_id
				FROM submissions s
				JOIN users u ON u.id = s.user_id
				WHERE s.user_id = ?
				  AND s.verdict = ?
				  AND u.role <> ?
			)
			SELECT p.difficulty, COUNT(*) AS solved_count
			FROM solved
			JOIN problems p ON p.id = solved.problem_id
			WHERE p.archived_at IS NULL
			GROUP BY p.difficulty
			""",
			(rs, rowNum) -> Map.entry(
				ProblemDifficulty.valueOf(rs.getString("difficulty")),
				rs.getLong("solved_count")
			),
			userId,
			SubmissionVerdict.ACCEPTED.name(),
			ADMIN_ROLE
		).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
		return result;
	}

	@Override
	public Map<String, Long> countSolvedProblemsByCategory(long userId) {
		Map<String, Long> result = new LinkedHashMap<>();
		jdbcTemplate.query(
			"""
			WITH solved AS (
				SELECT DISTINCT s.problem_id
				FROM submissions s
				JOIN users u ON u.id = s.user_id
				WHERE s.user_id = ?
				  AND s.verdict = ?
				  AND u.role <> ?
			)
			SELECT pc.name, COUNT(*) AS solved_count
			FROM solved
			JOIN problems p ON p.id = solved.problem_id
			JOIN problem_category_links pcl ON pcl.problem_id = p.id
			JOIN problem_categories pc ON pc.id = pcl.category_id
			WHERE p.archived_at IS NULL
			  AND pc.active = TRUE
			GROUP BY pc.id, pc.name, pc.display_order
			ORDER BY pc.display_order, pc.name
			""",
			(rs, rowNum) -> Map.entry(rs.getString("name"), rs.getLong("solved_count")),
			userId,
			SubmissionVerdict.ACCEPTED.name(),
			ADMIN_ROLE
		).forEach(entry -> result.put(entry.getKey(), entry.getValue()));
		return result;
	}

	private String solvedProblemFilters(long userId, SolvedProblemSearch search, List<Object> arguments) {
		arguments.add(userId);
		arguments.add(SubmissionVerdict.ACCEPTED.name());
		arguments.add(ADMIN_ROLE);
		StringBuilder filters = new StringBuilder();
		if (search.query() != null && !search.query().isBlank()) {
			filters.append(" AND LOWER(p.title) LIKE ?");
			arguments.add("%" + search.query().trim().toLowerCase(java.util.Locale.ROOT) + "%");
		}
		if (search.difficulty() != null) {
			filters.append(" AND p.difficulty = ?");
			arguments.add(search.difficulty().name());
		}
		if (search.category() != null && !search.category().isBlank()) {
			filters.append("""
				 AND EXISTS (
					SELECT 1
					FROM problem_category_links pcl
					JOIN problem_categories pc ON pc.id = pcl.category_id
					WHERE pcl.problem_id = p.id
					  AND pc.active = TRUE
					  AND pc.name = ?
				 )
				""");
			arguments.add(search.category().trim());
		}
		return filters.toString();
	}

	private String solvedProblemOrder(SolvedProblemSearch.Sort sort) {
		return switch (sort) {
			case PROBLEM_ID_ASC -> "p.id ASC";
			case PROBLEM_ID_DESC -> "p.id DESC";
			case SOLVED_AT_ASC -> "solved.solved_at ASC, p.id ASC";
			case SOLVED_AT_DESC -> "solved.solved_at DESC, p.id DESC";
		};
	}

	@Override
	public long countCreatedOn(LocalDate date) {
		Long count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM submissions s
			LEFT JOIN users u ON u.id = s.user_id
			WHERE s.created_at >= ?
			  AND s.created_at < ?
			  AND (s.user_id IS NULL OR u.role <> ?)
			""",
			Long.class,
			Timestamp.valueOf(date.atStartOfDay()),
			Timestamp.valueOf(date.plusDays(1).atStartOfDay()),
			ADMIN_ROLE
		);
		return count == null ? 0 : count;
	}

	@Override
	public long countDistinctUsersCreatedOn(LocalDate date) {
		Long count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(DISTINCT user_id)
			FROM submissions s
			JOIN users u ON u.id = s.user_id
			WHERE s.created_at >= ?
			  AND s.created_at < ?
			  AND u.role <> ?
			""",
			Long.class,
			Timestamp.valueOf(date.atStartOfDay()),
			Timestamp.valueOf(date.plusDays(1).atStartOfDay()),
			ADMIN_ROLE
		);
		return count == null ? 0 : count;
	}

	private Long getLongOrNull(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private Integer getIntegerOrNull(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM judge_jobs");
		jdbcTemplate.update("DELETE FROM submissions");
		jdbcTemplate.update("DELETE FROM guest_sessions");
	}
}
