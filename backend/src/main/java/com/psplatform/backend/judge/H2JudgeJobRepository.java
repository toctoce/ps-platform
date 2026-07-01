package com.psplatform.backend.judge;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.psplatform.backend.problem.ProblemTestCase;
import com.psplatform.backend.problem.ProblemTestCaseRepository;
import com.psplatform.backend.submission.JudgeJobStatus;
import com.psplatform.backend.submission.Submission;
import com.psplatform.backend.submission.SubmissionStatus;
import com.psplatform.backend.submission.SubmissionVerdict;

@Repository
public class H2JudgeJobRepository implements JudgeJobRepository {

	private final JdbcTemplate jdbcTemplate;
	private final ProblemTestCaseRepository problemTestCaseRepository;

	private final RowMapper<JudgeJobAssignment> assignmentRowMapper = (rs, rowNum) -> new JudgeJobAssignment(
		rs.getLong("job_id"),
		rs.getLong("submission_id"),
		rs.getLong("problem_id"),
		rs.getString("language"),
		rs.getString("source_code"),
		rs.getInt("time_limit_ms"),
		rs.getInt("memory_limit_mb"),
		List.of()
	);

	private final RowMapper<Submission> submissionRowMapper = (rs, rowNum) -> {
		Timestamp judgedAt = rs.getTimestamp("judged_at");
		String verdict = rs.getString("verdict");
		return new Submission(
			rs.getLong("id"),
			rs.getLong("problem_id"),
			getLongOrNull(rs, "user_id"),
			rs.getString("username"),
			rs.getString("language"),
			rs.getString("source_code"),
			SubmissionStatus.valueOf(rs.getString("status")),
			verdict == null ? null : SubmissionVerdict.valueOf(verdict),
			rs.getString("judge_message"),
			rs.getTimestamp("created_at").toLocalDateTime(),
			judgedAt == null ? null : judgedAt.toLocalDateTime()
		);
	};

	public H2JudgeJobRepository(JdbcTemplate jdbcTemplate, ProblemTestCaseRepository problemTestCaseRepository) {
		this.jdbcTemplate = jdbcTemplate;
		this.problemTestCaseRepository = problemTestCaseRepository;
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
	public Submission complete(long jobId, SubmissionVerdict verdict, String judgeMessage) {
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
				SET status = ?, verdict = ?, judge_message = ?, judged_at = CURRENT_TIMESTAMP
				WHERE id = (
					SELECT submission_id
					FROM judge_jobs
					WHERE id = ?
				)
				"""
			);
			ps.setString(1, SubmissionStatus.JUDGED.name());
			ps.setString(2, verdict.name());
			ps.setString(3, judgeMessage);
			ps.setLong(4, jobId);
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

	@Override
	public int requeueStale(long staleAfterSeconds) {
		LocalDateTime staleBefore = LocalDateTime.now().minusSeconds(staleAfterSeconds);

		jdbcTemplate.update(
			"""
			UPDATE submissions
			SET status = ?
			WHERE id IN (
				SELECT submission_id
				FROM judge_jobs
				WHERE status = ? AND picked_at < ?
			)
			""",
			SubmissionStatus.PENDING.name(),
			JudgeJobStatus.RUNNING.name(),
			Timestamp.valueOf(staleBefore)
		);

		return jdbcTemplate.update(
			"""
			UPDATE judge_jobs
			SET status = ?, picked_at = NULL
			WHERE status = ? AND picked_at < ?
			""",
			JudgeJobStatus.QUEUED.name(),
			JudgeJobStatus.RUNNING.name(),
			Timestamp.valueOf(staleBefore)
		);
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
		return assignments.stream()
			.findFirst()
			.map(this::withTestCases);
	}

	private JudgeJobAssignment withTestCases(JudgeJobAssignment assignment) {
		List<JudgeTestCase> testCases = problemTestCaseRepository.findByProblemId(assignment.problemId())
			.stream()
			.map(this::toJudgeTestCase)
			.toList();

		return new JudgeJobAssignment(
			assignment.jobId(),
			assignment.submissionId(),
			assignment.problemId(),
			assignment.language(),
			assignment.sourceCode(),
			assignment.timeLimitMs(),
			assignment.memoryLimitMb(),
			testCases
		);
	}

	private JudgeTestCase toJudgeTestCase(ProblemTestCase testCase) {
		return new JudgeTestCase(testCase.getInput(), testCase.getExpectedOutput());
	}

	private Optional<Submission> findSubmissionByJobId(long jobId) {
		List<Submission> submissions = jdbcTemplate.query(
			"""
			SELECT s.id, s.problem_id, s.user_id, u.username, s.language, s.source_code, s.status,
			       s.verdict, s.judge_message, s.created_at, s.judged_at
			FROM submissions s
			LEFT JOIN users u ON u.id = s.user_id
			JOIN judge_jobs jj ON jj.submission_id = s.id
			WHERE jj.id = ?
			""",
			submissionRowMapper,
			jobId
		);
		return submissions.stream().findFirst();
	}

	private Long getLongOrNull(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
}
