package com.psplatform.backend.submission;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class H2SubmissionRepository implements SubmissionRepository {

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
			rs.getTimestamp("created_at").toLocalDateTime(),
			judgedAt == null ? null : judgedAt.toLocalDateTime()
		);
	};

	public H2SubmissionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Submission save(long problemId, Long userId, SubmissionCreateRequest request) {
		String sql = """
			INSERT INTO submissions (
				problem_id,
				user_id,
				language,
				source_code,
				status
			)
			VALUES (?, ?, ?, ?, ?)
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
			ps.setString(3, request.language());
			ps.setString(4, request.sourceCode());
			ps.setString(5, SubmissionStatus.PENDING.name());
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
			ORDER BY id DESC
			""",
			rowMapper,
			problemId
		);
	}

	@Override
	public long countCreatedOn(LocalDate date) {
		Long count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(*)
			FROM submissions
			WHERE created_at >= ?
			  AND created_at < ?
			""",
			Long.class,
			Timestamp.valueOf(date.atStartOfDay()),
			Timestamp.valueOf(date.plusDays(1).atStartOfDay())
		);
		return count == null ? 0 : count;
	}

	@Override
	public long countDistinctUsersCreatedOn(LocalDate date) {
		Long count = jdbcTemplate.queryForObject(
			"""
			SELECT COUNT(DISTINCT user_id)
			FROM submissions
			WHERE user_id IS NOT NULL
			  AND created_at >= ?
			  AND created_at < ?
			""",
			Long.class,
			Timestamp.valueOf(date.atStartOfDay()),
			Timestamp.valueOf(date.plusDays(1).atStartOfDay())
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
	}
}
