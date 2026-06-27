package com.psplatform.backend.judge;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.psplatform.backend.submission.JudgeJobStatus;
import com.psplatform.backend.submission.Submission;
import com.psplatform.backend.submission.SubmissionStatus;
import com.psplatform.backend.submission.SubmissionVerdict;

@Repository
public class H2JudgeJobRepository implements JudgeJobRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<JudgeJobAssignment> assignmentRowMapper = (rs, rowNum) -> new JudgeJobAssignment(
		rs.getLong("job_id"),
		rs.getLong("submission_id"),
		rs.getLong("problem_id"),
		rs.getString("language"),
		rs.getString("source_code"),
		rs.getInt("time_limit_ms"),
		rs.getInt("memory_limit_mb")
	);

	private final RowMapper<Submission> submissionRowMapper = (rs, rowNum) -> {
		Timestamp judgedAt = rs.getTimestamp("judged_at");
		String verdict = rs.getString("verdict");
		return new Submission(
			rs.getLong("id"),
			rs.getLong("problem_id"),
			rs.getString("language"),
			rs.getString("source_code"),
			SubmissionStatus.valueOf(rs.getString("status")),
			verdict == null ? null : SubmissionVerdict.valueOf(verdict),
			rs.getTimestamp("created_at").toLocalDateTime(),
			judgedAt == null ? null : judgedAt.toLocalDateTime()
		);
	};

	public H2JudgeJobRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void create(long submissionId) {
		jdbcTemplate.update(
			"""
			INSERT INTO judge_jobs (submission_id, status)
			VALUES (?, ?)
			""",
			submissionId,
			JudgeJobStatus.QUEUED.name()
		);
	}

	@Override
	public Optional<JudgeJobAssignment> findNextAndMarkRunning() {
		List<Long> jobIds = jdbcTemplate.query(
			"""
			SELECT id
			FROM judge_jobs
			WHERE status = ?
			ORDER BY id
			LIMIT 1
			""",
			(rs, rowNum) -> rs.getLong("id"),
			JudgeJobStatus.QUEUED.name()
		);
		if (jobIds.isEmpty()) {
			return Optional.empty();
		}

		long jobId = jobIds.getFirst();
		int updatedJobs = jdbcTemplate.update(
			"""
			UPDATE judge_jobs
			SET status = ?, picked_at = CURRENT_TIMESTAMP
			WHERE id = ? AND status = ?
			""",
			JudgeJobStatus.RUNNING.name(),
			jobId,
			JudgeJobStatus.QUEUED.name()
		);
		if (updatedJobs == 0) {
			return Optional.empty();
		}

		jdbcTemplate.update(
			"""
			UPDATE submissions
			SET status = ?
			WHERE id = (
				SELECT submission_id
				FROM judge_jobs
				WHERE id = ?
			)
			""",
			SubmissionStatus.RUNNING.name(),
			jobId
		);

		return findAssignmentByJobId(jobId);
	}

	@Override
	public Submission complete(long jobId, SubmissionVerdict verdict) {
		int updatedJobs = jdbcTemplate.update(
			"""
			UPDATE judge_jobs
			SET status = ?, finished_at = CURRENT_TIMESTAMP
			WHERE id = ? AND status = ?
			""",
			JudgeJobStatus.DONE.name(),
			jobId,
			JudgeJobStatus.RUNNING.name()
		);
		if (updatedJobs == 0) {
			throw new JudgeJobNotFoundException(jobId);
		}

		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
				"""
				UPDATE submissions
				SET status = ?, verdict = ?, judged_at = CURRENT_TIMESTAMP
				WHERE id = (
					SELECT submission_id
					FROM judge_jobs
					WHERE id = ?
				)
				"""
			);
			ps.setString(1, SubmissionStatus.JUDGED.name());
			ps.setString(2, verdict.name());
			ps.setLong(3, jobId);
			return ps;
		});

		return findSubmissionByJobId(jobId)
			.orElseThrow(() -> new JudgeJobNotFoundException(jobId));
	}

	@Override
	public int countBySubmissionId(long submissionId) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM judge_jobs WHERE submission_id = ?",
			Integer.class,
			submissionId
		);
		return count == null ? 0 : count;
	}

	private Optional<JudgeJobAssignment> findAssignmentByJobId(long jobId) {
		List<JudgeJobAssignment> assignments = jdbcTemplate.query(
			"""
			SELECT jj.id AS job_id, s.id AS submission_id, s.problem_id, s.language,
			       s.source_code, p.time_limit_ms, p.memory_limit_mb
			FROM judge_jobs jj
			JOIN submissions s ON s.id = jj.submission_id
			JOIN problems p ON p.id = s.problem_id
			WHERE jj.id = ?
			""",
			assignmentRowMapper,
			jobId
		);
		return assignments.stream().findFirst();
	}

	private Optional<Submission> findSubmissionByJobId(long jobId) {
		List<Submission> submissions = jdbcTemplate.query(
			"""
			SELECT s.id, s.problem_id, s.language, s.source_code, s.status,
			       s.verdict, s.created_at, s.judged_at
			FROM submissions s
			JOIN judge_jobs jj ON jj.submission_id = s.id
			WHERE jj.id = ?
			""",
			submissionRowMapper,
			jobId
		);
		return submissions.stream().findFirst();
	}
}
