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
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
	private final ObjectMapper objectMapper;

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

	public H2JudgeJobRepository(
		JdbcTemplate jdbcTemplate,
		ProblemTestCaseRepository problemTestCaseRepository
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.problemTestCaseRepository = problemTestCaseRepository;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public void create(long submissionId) {
		jdbcTemplate.update(
			"""
			INSERT INTO judge_jobs (job_kind, submission_id, status)
			VALUES (?, ?, ?)
			""",
			JudgeJobKind.SUBMISSION.name(),
			submissionId,
			JudgeJobStatus.QUEUED.name()
		);
	}

	@Override
	public CodeRunResponse createCodeRun(long problemId, int timeLimitMs, int memoryLimitMb, CodeRunCreateRequest request) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
				"""
				INSERT INTO judge_jobs (
					job_kind, problem_id, language, source_code, test_cases_json, status
				)
				VALUES (?, ?, ?, ?, ?, ?)
				""",
				new String[] {"id"}
			);
			ps.setString(1, JudgeJobKind.CODE_RUN.name());
			ps.setLong(2, problemId);
			ps.setString(3, request.language());
			ps.setString(4, request.sourceCode());
			ps.setString(5, writeJson(request.testCases() == null ? List.of() : request.testCases()));
			ps.setString(6, JudgeJobStatus.QUEUED.name());
			return ps;
		}, keyHolder);

		Number jobId = keyHolder.getKey();
		if (jobId == null) {
			throw new IllegalStateException("Judge job ID was not generated");
		}
		return new CodeRunResponse(
			jobId.longValue(),
			problemId,
			request.language(),
			JudgeJobStatus.QUEUED,
			null,
			null,
			List.of()
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

		if (findJobKind(jobId) == JudgeJobKind.SUBMISSION) {
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
		}

		return findAssignmentByJobId(jobId);
	}

	@Override
	public JudgeJobCompleteResult complete(
		long jobId,
		SubmissionVerdict verdict,
		String judgeMessage,
		List<JudgeCaseResult> caseResults
	) {
		int updatedJobs = jdbcTemplate.update(
			"""
			UPDATE judge_jobs
			SET status = ?, verdict = ?, judge_message = ?, result_json = ?, finished_at = CURRENT_TIMESTAMP
			WHERE id = ? AND status = ?
			""",
			JudgeJobStatus.DONE.name(),
			verdict.name(),
			judgeMessage,
			writeJson(caseResults == null ? List.of() : caseResults),
			jobId,
			JudgeJobStatus.RUNNING.name()
		);
		if (updatedJobs == 0) {
			throw new JudgeJobNotFoundException(jobId);
		}

		JudgeJobKind jobKind = findJobKind(jobId);
		if (jobKind == JudgeJobKind.CODE_RUN) {
			return JudgeJobCompleteResult.codeRun(
				findCodeRunByJobId(jobId).orElseThrow(() -> new JudgeJobNotFoundException(jobId))
			);
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

		Submission submission = findSubmissionByJobId(jobId)
			.orElseThrow(() -> new JudgeJobNotFoundException(jobId));
		return JudgeJobCompleteResult.submission(submission);
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
				WHERE job_kind = ? AND status = ? AND picked_at < ?
			)
			""",
			SubmissionStatus.PENDING.name(),
			JudgeJobKind.SUBMISSION.name(),
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
			SELECT jj.id AS job_id, jj.job_kind, s.id AS submission_id,
			       COALESCE(jj.problem_id, s.problem_id) AS problem_id,
			       COALESCE(jj.language, s.language) AS language,
			       COALESCE(jj.source_code, s.source_code) AS source_code,
			       p.time_limit_ms, p.memory_limit_mb, jj.test_cases_json
			FROM judge_jobs jj
			LEFT JOIN submissions s ON s.id = jj.submission_id
			JOIN problems p ON p.id = COALESCE(jj.problem_id, s.problem_id)
			WHERE jj.id = ?
			""",
			this::mapAssignment,
			jobId
		);
		return assignments.stream()
			.findFirst()
			.map(this::withTestCases);
	}

	private JudgeJobAssignment withTestCases(JudgeJobAssignment assignment) {
		if (assignment.jobKind() == JudgeJobKind.CODE_RUN) {
			return assignment;
		}

		List<JudgeTestCase> testCases = problemTestCaseRepository.findByProblemId(assignment.problemId())
			.stream()
			.map(this::toJudgeTestCase)
			.toList();

		return new JudgeJobAssignment(
			assignment.jobId(),
			assignment.jobKind(),
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

	@Override
	public Optional<CodeRunResponse> findCodeRunByJobId(long jobId) {
		List<CodeRunResponse> codeRuns = jdbcTemplate.query(
			"""
			SELECT id, problem_id, language, status, verdict, judge_message, result_json
			FROM judge_jobs
			WHERE id = ? AND job_kind = ?
			""",
			(rs, rowNum) -> new CodeRunResponse(
				rs.getLong("id"),
				rs.getLong("problem_id"),
				rs.getString("language"),
				JudgeJobStatus.valueOf(rs.getString("status")),
				parseVerdict(rs.getString("verdict")),
				rs.getString("judge_message"),
				readCaseResults(rs.getString("result_json"))
			),
			jobId,
			JudgeJobKind.CODE_RUN.name()
		);
		return codeRuns.stream().findFirst();
	}

	private Optional<Submission> findSubmissionByJobId(long jobId) {
		List<Submission> submissions = jdbcTemplate.query(
			"""
			SELECT s.id, s.problem_id, s.user_id,
			       CASE WHEN u.deleted_at IS NOT NULL THEN '탈퇴한 사용자' ELSE u.username END AS username,
			       s.language, s.source_code, s.status,
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

	private JudgeJobAssignment mapAssignment(ResultSet rs, int rowNum) throws SQLException {
		JudgeJobKind jobKind = JudgeJobKind.valueOf(rs.getString("job_kind"));
		List<JudgeTestCase> testCases = jobKind == JudgeJobKind.CODE_RUN
			? readTestCases(rs.getString("test_cases_json"))
			: List.of();
		return new JudgeJobAssignment(
			rs.getLong("job_id"),
			jobKind,
			getLongOrNull(rs, "submission_id"),
			rs.getLong("problem_id"),
			rs.getString("language"),
			rs.getString("source_code"),
			rs.getInt("time_limit_ms"),
			rs.getInt("memory_limit_mb"),
			testCases
		);
	}

	private JudgeJobKind findJobKind(long jobId) {
		String value = jdbcTemplate.queryForObject(
			"SELECT job_kind FROM judge_jobs WHERE id = ?",
			String.class,
			jobId
		);
		if (value == null) {
			throw new JudgeJobNotFoundException(jobId);
		}
		return JudgeJobKind.valueOf(value);
	}

	private SubmissionVerdict parseVerdict(String value) {
		return value == null ? null : SubmissionVerdict.valueOf(value);
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Failed to serialize judge job payload", exception);
		}
	}

	private List<JudgeTestCase> readTestCases(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(value, new TypeReference<List<JudgeTestCase>>() {});
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Failed to deserialize judge test cases", exception);
		}
	}

	private List<JudgeCaseResult> readCaseResults(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(value, new TypeReference<List<JudgeCaseResult>>() {});
		} catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("Failed to deserialize judge case results", exception);
		}
	}

	private Long getLongOrNull(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}
}
