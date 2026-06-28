package com.psplatform.backend.submission;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
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
		return new Submission(
			rs.getLong("id"),
			rs.getLong("problem_id"),
			rs.getString("language"),
			rs.getString("source_code"),
			SubmissionStatus.valueOf(rs.getString("status")),
			verdict == null ? null : SubmissionVerdict.valueOf(verdict),
			rs.getString("judge_message"),
			rs.getTimestamp("created_at").toLocalDateTime(),
			judgedAt == null ? null : judgedAt.toLocalDateTime()
		);
	};

	public H2SubmissionRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public Submission save(long problemId, SubmissionCreateRequest request) {
		String sql = """
			INSERT INTO submissions (
				problem_id,
				language,
				source_code,
				status
			)
			VALUES (?, ?, ?, ?)
			""";

		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, new String[] {"id"});
			ps.setLong(1, problemId);
			ps.setString(2, request.language());
			ps.setString(3, request.sourceCode());
			ps.setString(4, SubmissionStatus.PENDING.name());
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
			SELECT id, problem_id, language, source_code, status, verdict, judge_message, created_at, judged_at
			FROM submissions
			WHERE id = ?
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
			SELECT id, problem_id, language, source_code, status, verdict, judge_message, created_at, judged_at
			FROM submissions
			WHERE problem_id = ?
			ORDER BY id DESC
			""",
			rowMapper,
			problemId
		);
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM judge_jobs");
		jdbcTemplate.update("DELETE FROM submissions");
	}
}
