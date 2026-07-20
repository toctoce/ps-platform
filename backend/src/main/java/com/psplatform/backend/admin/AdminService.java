package com.psplatform.backend.admin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.admin.AdminModels.AuditLog;
import com.psplatform.backend.admin.AdminModels.CategorySummary;
import com.psplatform.backend.admin.AdminModels.CategoryUpdateRequest;
import com.psplatform.backend.admin.AdminModels.Dashboard;
import com.psplatform.backend.admin.AdminModels.Example;
import com.psplatform.backend.admin.AdminModels.JudgeJobSummary;
import com.psplatform.backend.admin.AdminModels.ProblemDetail;
import com.psplatform.backend.admin.AdminModels.ProblemSummary;
import com.psplatform.backend.admin.AdminModels.ProblemUpdateRequest;
import com.psplatform.backend.admin.AdminModels.SubmissionSummary;
import com.psplatform.backend.admin.AdminModels.TestCase;
import com.psplatform.backend.admin.AdminModels.UserSummary;
import com.psplatform.backend.admin.AdminModels.UserUpdateRequest;
import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemCategoryRepository;
import com.psplatform.backend.problem.ProblemCreateRequest;
import com.psplatform.backend.problem.ProblemDifficulty;
import com.psplatform.backend.problem.ProblemExampleCreateRequest;
import com.psplatform.backend.problem.ProblemService;
import com.psplatform.backend.problem.ProblemTestCaseCreateRequest;
import com.psplatform.backend.user.User;

@Service
public class AdminService {

	private static final Set<String> EDITABLE_ROLES = Set.of("USER", "ADMIN");
	private static final int LIST_LIMIT = 500;

	private final JdbcTemplate jdbcTemplate;
	private final ProblemService problemService;
	private final ProblemCategoryRepository problemCategoryRepository;

	public AdminService(
		JdbcTemplate jdbcTemplate,
		ProblemService problemService,
		ProblemCategoryRepository problemCategoryRepository
	) {
		this.jdbcTemplate = jdbcTemplate;
		this.problemService = problemService;
		this.problemCategoryRepository = problemCategoryRepository;
	}

	public Dashboard dashboard() {
		return new Dashboard(
			count("SELECT COUNT(*) FROM problems WHERE archived_at IS NULL"),
			count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL"),
			count("SELECT COUNT(*) FROM submissions"),
			count("SELECT COUNT(*) FROM problem_reports WHERE status = 'PENDING'"),
			count("SELECT COUNT(*) FROM judge_jobs WHERE status = 'QUEUED'"),
			count("SELECT COUNT(*) FROM judge_jobs WHERE status = 'RUNNING'")
		);
	}

	public List<ProblemSummary> findProblems(boolean includeArchived) {
		String archivedCondition = includeArchived ? "" : "WHERE p.archived_at IS NULL";
		return jdbcTemplate.query(
			"""
			SELECT p.id, p.title, p.difficulty, p.created_at, p.archived_at,
			       (SELECT COUNT(*) FROM problem_examples pe WHERE pe.problem_id = p.id) AS example_count,
			       (SELECT COUNT(*) FROM problem_test_cases pt WHERE pt.problem_id = p.id) AS test_case_count,
			       (SELECT COUNT(*) FROM submissions s WHERE s.problem_id = p.id) AS submission_count
			FROM problems p
			%s
			ORDER BY p.id DESC
			""".formatted(archivedCondition),
			(rs, rowNum) -> new ProblemSummary(
				rs.getLong("id"),
				rs.getString("title"),
				ProblemDifficulty.valueOf(rs.getString("difficulty")),
				rs.getLong("example_count"),
				rs.getLong("test_case_count"),
				rs.getLong("submission_count"),
				localDateTime(rs, "created_at"),
				localDateTime(rs, "archived_at")
			)
		);
	}

	public ProblemDetail findProblem(long problemId) {
		List<ProblemDetail> problems = jdbcTemplate.query(
			"""
			SELECT id, title, description, solution, input_description, input_constraints,
			       output_description, time_limit_ms, memory_limit_mb, difficulty, created_at, archived_at
			FROM problems
			WHERE id = ?
			""",
			(rs, rowNum) -> new ProblemDetail(
				rs.getLong("id"),
				rs.getString("title"),
				rs.getString("description"),
				rs.getString("solution"),
				rs.getString("input_description"),
				rs.getString("input_constraints"),
				rs.getString("output_description"),
				rs.getInt("time_limit_ms"),
				rs.getInt("memory_limit_mb"),
				ProblemDifficulty.valueOf(rs.getString("difficulty")),
				findCategoryNames(problemId),
				findExamples(problemId),
				findTestCases(problemId),
				localDateTime(rs, "created_at"),
				localDateTime(rs, "archived_at")
			),
			problemId
		);
		if (problems.isEmpty()) {
			throw new AdminInvalidRequestException("문제를 찾을 수 없습니다.");
		}
		return problems.getFirst();
	}

	@Transactional
	public Problem createProblem(User admin, ProblemCreateRequest request) {
		Problem problem = problemService.create(request);
		audit(admin, "PROBLEM_CREATE", "PROBLEM", problem.getId().toString(), problem.getTitle());
		return problem;
	}

	@Transactional
	public ProblemDetail updateProblem(User admin, long problemId, ProblemUpdateRequest request) {
		validateProblem(request);
		int updated = jdbcTemplate.update(
			"""
			UPDATE problems
			SET title = ?, description = ?, solution = ?, input_description = ?, input_constraints = ?,
			    output_description = ?, time_limit_ms = ?, memory_limit_mb = ?, difficulty = ?
			WHERE id = ?
			""",
			request.title().trim(),
			request.description(),
			nullIfBlank(request.solution()),
			request.inputDescription(),
			nullIfBlank(request.inputConstraints()),
			request.outputDescription(),
			request.timeLimitMs(),
			request.memoryLimitMb(),
			request.difficulty().name(),
			problemId
		);
		assertUpdated(updated, "문제를 찾을 수 없습니다.");
		jdbcTemplate.update("DELETE FROM problem_category_links WHERE problem_id = ?", problemId);
		problemCategoryRepository.addLinks(problemId, request.categories());
		audit(admin, "PROBLEM_UPDATE", "PROBLEM", Long.toString(problemId), request.title());
		return findProblem(problemId);
	}

	@Transactional
	public void archiveProblem(User admin, long problemId) {
		int updated = jdbcTemplate.update(
			"UPDATE problems SET archived_at = CURRENT_TIMESTAMP WHERE id = ? AND archived_at IS NULL",
			problemId
		);
		assertUpdated(updated, "문제를 찾을 수 없거나 이미 보관되었습니다.");
		audit(admin, "PROBLEM_ARCHIVE", "PROBLEM", Long.toString(problemId), null);
	}

	@Transactional
	public void restoreProblem(User admin, long problemId) {
		int updated = jdbcTemplate.update(
			"UPDATE problems SET archived_at = NULL WHERE id = ? AND archived_at IS NOT NULL",
			problemId
		);
		assertUpdated(updated, "문제를 찾을 수 없거나 보관 상태가 아닙니다.");
		audit(admin, "PROBLEM_RESTORE", "PROBLEM", Long.toString(problemId), null);
	}

	@Transactional
	public TestCase addTestCase(User admin, long problemId, ProblemTestCaseCreateRequest request) {
		var saved = problemService.addTestCase(problemId, request);
		audit(admin, "TEST_CASE_CREATE", "PROBLEM", Long.toString(problemId), "testCase=" + saved.getId());
		return new TestCase(saved.getId(), saved.getInput(), saved.getExpectedOutput(), saved.getSortOrder());
	}

	@Transactional
	public void updateTestCase(User admin, long problemId, long testCaseId, ProblemTestCaseCreateRequest request) {
		int updated = jdbcTemplate.update(
			"UPDATE problem_test_cases SET input_data = ?, expected_output = ? WHERE id = ? AND problem_id = ?",
			request.input(), request.expectedOutput(), testCaseId, problemId
		);
		assertUpdated(updated, "테스트케이스를 찾을 수 없습니다.");
		audit(admin, "TEST_CASE_UPDATE", "TEST_CASE", Long.toString(testCaseId), "problem=" + problemId);
	}

	@Transactional
	public void deleteTestCase(User admin, long problemId, long testCaseId) {
		int updated = jdbcTemplate.update(
			"DELETE FROM problem_test_cases WHERE id = ? AND problem_id = ?",
			testCaseId, problemId
		);
		assertUpdated(updated, "테스트케이스를 찾을 수 없습니다.");
		audit(admin, "TEST_CASE_DELETE", "TEST_CASE", Long.toString(testCaseId), "problem=" + problemId);
	}

	@Transactional
	public Example addExample(User admin, long problemId, ProblemExampleCreateRequest request) {
		var saved = problemService.addExample(problemId, request);
		audit(admin, "EXAMPLE_CREATE", "PROBLEM", Long.toString(problemId), "example=" + saved.getId());
		return new Example(
			saved.getId(), saved.getInput(), saved.getExpectedOutput(), saved.getDescription(), saved.getSortOrder()
		);
	}

	@Transactional
	public void updateExample(User admin, long problemId, long exampleId, ProblemExampleCreateRequest request) {
		int updated = jdbcTemplate.update(
			"""
			UPDATE problem_examples
			SET input_data = ?, expected_output = ?, description = ?
			WHERE id = ? AND problem_id = ?
			""",
			request.input(), request.expectedOutput(), nullIfBlank(request.description()), exampleId, problemId
		);
		assertUpdated(updated, "예제를 찾을 수 없습니다.");
		audit(admin, "EXAMPLE_UPDATE", "EXAMPLE", Long.toString(exampleId), "problem=" + problemId);
	}

	@Transactional
	public void deleteExample(User admin, long problemId, long exampleId) {
		int updated = jdbcTemplate.update(
			"DELETE FROM problem_examples WHERE id = ? AND problem_id = ?",
			exampleId, problemId
		);
		assertUpdated(updated, "예제를 찾을 수 없습니다.");
		audit(admin, "EXAMPLE_DELETE", "EXAMPLE", Long.toString(exampleId), "problem=" + problemId);
	}

	public List<UserSummary> findUsers() {
		return jdbcTemplate.query(
			"""
			SELECT u.id, u.email, u.username, u.role, u.created_at, u.suspended_at, u.deleted_at,
			       COUNT(s.id) AS submission_count,
			       SUM(CASE WHEN s.verdict = 'ACCEPTED' THEN 1 ELSE 0 END) AS accepted_count,
			       COUNT(DISTINCT CASE WHEN s.verdict = 'ACCEPTED' THEN s.problem_id END) AS solved_count
			FROM users u
			LEFT JOIN submissions s ON s.user_id = u.id
			GROUP BY u.id, u.email, u.username, u.role, u.created_at, u.suspended_at, u.deleted_at
			ORDER BY u.id DESC
			LIMIT %d
			""".formatted(LIST_LIMIT),
			(rs, rowNum) -> new UserSummary(
				rs.getLong("id"), rs.getString("email"), rs.getString("username"), rs.getString("role"),
				rs.getLong("solved_count"), rs.getLong("submission_count"), rs.getLong("accepted_count"),
				instant(rs, "created_at"), instant(rs, "suspended_at"), instant(rs, "deleted_at")
			)
		);
	}

	@Transactional
	public void updateUser(User admin, long userId, UserUpdateRequest request) {
		if (admin.getId().equals(userId)) {
			throw new AdminInvalidRequestException("현재 로그인한 관리자 계정은 변경할 수 없습니다.");
		}
		if (!EDITABLE_ROLES.contains(request.role())) {
			throw new AdminInvalidRequestException("지원하지 않는 회원 권한입니다.");
		}
		int updated = jdbcTemplate.update(
			"""
			UPDATE users
			SET role = ?, suspended_at = CASE WHEN ? THEN COALESCE(suspended_at, CURRENT_TIMESTAMP) ELSE NULL END,
			    updated_at = CURRENT_TIMESTAMP
			WHERE id = ? AND deleted_at IS NULL
			""",
			request.role(), request.suspended(), userId
		);
		assertUpdated(updated, "회원을 찾을 수 없습니다.");
		jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
		audit(admin, "USER_UPDATE", "USER", Long.toString(userId),
			"role=" + request.role() + ", suspended=" + request.suspended());
	}

	public List<SubmissionSummary> findSubmissions() {
		return jdbcTemplate.query(
			"""
			SELECT s.id, s.problem_id, p.title AS problem_title, s.user_id,
			       CASE WHEN u.deleted_at IS NOT NULL THEN '탈퇴한 사용자' ELSE u.username END AS username,
			       s.language, s.status, s.verdict, s.execution_time_ms, s.memory_mb, s.created_at, s.judged_at
			FROM submissions s
			JOIN problems p ON p.id = s.problem_id
			LEFT JOIN users u ON u.id = s.user_id
			ORDER BY s.id DESC
			LIMIT %d
			""".formatted(LIST_LIMIT),
			(rs, rowNum) -> new SubmissionSummary(
				rs.getLong("id"), rs.getLong("problem_id"), rs.getString("problem_title"),
				longValue(rs, "user_id"), rs.getString("username"), rs.getString("language"),
				rs.getString("status"), rs.getString("verdict"), integerValue(rs, "execution_time_ms"),
				integerValue(rs, "memory_mb"), instant(rs, "created_at"), instant(rs, "judged_at")
			)
		);
	}

	public List<JudgeJobSummary> findJudgeJobs() {
		return jdbcTemplate.query(
			"""
			SELECT jj.id, jj.job_kind, jj.submission_id, COALESCE(jj.problem_id, s.problem_id) AS problem_id,
			       jj.status, jj.verdict, jj.created_at, jj.picked_at, jj.finished_at
			FROM judge_jobs jj
			LEFT JOIN submissions s ON s.id = jj.submission_id
			ORDER BY jj.id DESC
			LIMIT %d
			""".formatted(LIST_LIMIT),
			(rs, rowNum) -> new JudgeJobSummary(
				rs.getLong("id"), rs.getString("job_kind"), longValue(rs, "submission_id"),
				longValue(rs, "problem_id"), rs.getString("status"), rs.getString("verdict"),
				instant(rs, "created_at"), instant(rs, "picked_at"), instant(rs, "finished_at")
			)
		);
	}

	@Transactional
	public void requeueJudgeJob(User admin, long jobId) {
		String status = jdbcTemplate.query(
			"SELECT status FROM judge_jobs WHERE id = ?",
			(rs, rowNum) -> rs.getString("status"),
			jobId
		).stream().findFirst().orElseThrow(() -> new AdminInvalidRequestException("채점 작업을 찾을 수 없습니다."));
		if ("RUNNING".equals(status)) {
			throw new AdminInvalidRequestException("실행 중인 채점 작업은 다시 대기시킬 수 없습니다.");
		}
		jdbcTemplate.update(
			"""
			UPDATE judge_jobs
			SET status = 'QUEUED', picked_at = NULL, finished_at = NULL,
			    verdict = NULL, judge_message = NULL, result_json = NULL
			WHERE id = ?
			""",
			jobId
		);
		jdbcTemplate.update(
			"""
			UPDATE submissions
			SET status = 'PENDING', verdict = NULL, judge_message = NULL,
			    execution_time_ms = NULL, memory_mb = NULL, judged_at = NULL
			WHERE id = (SELECT submission_id FROM judge_jobs WHERE id = ?)
			""",
			jobId
		);
		audit(admin, "JUDGE_JOB_REQUEUE", "JUDGE_JOB", Long.toString(jobId), null);
	}

	public List<CategorySummary> findCategories() {
		return jdbcTemplate.query(
			"""
			SELECT pc.id, pc.name, pc.display_order, pc.active, COUNT(p.id) AS problem_count
			FROM problem_categories pc
			LEFT JOIN problem_category_links pcl ON pcl.category_id = pc.id
			LEFT JOIN problems p ON p.id = pcl.problem_id AND p.archived_at IS NULL
			GROUP BY pc.id, pc.name, pc.display_order, pc.active
			ORDER BY pc.display_order, pc.name
			""",
			(rs, rowNum) -> new CategorySummary(
				rs.getLong("id"), rs.getString("name"), rs.getInt("display_order"),
				rs.getBoolean("active"), rs.getLong("problem_count")
			)
		);
	}

	@Transactional
	public void createCategory(User admin, CategoryUpdateRequest request) {
		if (request.name() == null || request.name().isBlank()) {
			throw new AdminInvalidRequestException("카테고리 이름을 입력해 주세요.");
		}
		jdbcTemplate.update(
			"INSERT INTO problem_categories (name, display_order, active) VALUES (?, ?, ?)",
			request.name().trim(), request.displayOrder(), request.active()
		);
		Long categoryId = jdbcTemplate.queryForObject(
			"SELECT id FROM problem_categories WHERE name = ?",
			Long.class,
			request.name().trim()
		);
		audit(admin, "CATEGORY_CREATE", "CATEGORY", categoryId == null ? null : categoryId.toString(), request.name());
	}

	@Transactional
	public void updateCategory(User admin, long categoryId, CategoryUpdateRequest request) {
		if (request.name() == null || request.name().isBlank()) {
			throw new AdminInvalidRequestException("카테고리 이름을 입력해 주세요.");
		}
		int updated = jdbcTemplate.update(
			"UPDATE problem_categories SET name = ?, display_order = ?, active = ? WHERE id = ?",
			request.name().trim(), request.displayOrder(), request.active(), categoryId
		);
		assertUpdated(updated, "카테고리를 찾을 수 없습니다.");
		audit(admin, "CATEGORY_UPDATE", "CATEGORY", Long.toString(categoryId), request.name());
	}

	public List<AuditLog> findAuditLogs() {
		return jdbcTemplate.query(
			"""
			SELECT aal.id, aal.admin_user_id, u.username, aal.action, aal.target_type,
			       aal.target_id, aal.detail, aal.created_at
			FROM admin_audit_logs aal
			JOIN users u ON u.id = aal.admin_user_id
			ORDER BY aal.id DESC
			LIMIT %d
			""".formatted(LIST_LIMIT),
			(rs, rowNum) -> new AuditLog(
				rs.getLong("id"), rs.getLong("admin_user_id"), rs.getString("username"),
				rs.getString("action"), rs.getString("target_type"), rs.getString("target_id"),
				rs.getString("detail"), localDateTime(rs, "created_at")
			)
		);
	}

	@Transactional
	public void audit(User admin, String action, String targetType, String targetId, String detail) {
		jdbcTemplate.update(
			"""
			INSERT INTO admin_audit_logs (admin_user_id, action, target_type, target_id, detail)
			VALUES (?, ?, ?, ?, ?)
			""",
			admin.getId(), action, targetType, targetId, detail
		);
	}

	private List<String> findCategoryNames(long problemId) {
		return jdbcTemplate.queryForList(
			"""
			SELECT pc.name
			FROM problem_category_links pcl
			JOIN problem_categories pc ON pc.id = pcl.category_id
			WHERE pcl.problem_id = ?
			ORDER BY pc.display_order, pc.name
			""",
			String.class,
			problemId
		);
	}

	private List<TestCase> findTestCases(long problemId) {
		return jdbcTemplate.query(
			"""
			SELECT id, input_data, expected_output, sort_order
			FROM problem_test_cases WHERE problem_id = ? ORDER BY sort_order
			""",
			(rs, rowNum) -> new TestCase(
				rs.getLong("id"), rs.getString("input_data"), rs.getString("expected_output"), rs.getInt("sort_order")
			),
			problemId
		);
	}

	private List<Example> findExamples(long problemId) {
		return jdbcTemplate.query(
			"""
			SELECT id, input_data, expected_output, description, sort_order
			FROM problem_examples WHERE problem_id = ? ORDER BY sort_order
			""",
			(rs, rowNum) -> new Example(
				rs.getLong("id"), rs.getString("input_data"), rs.getString("expected_output"),
				rs.getString("description"), rs.getInt("sort_order")
			),
			problemId
		);
	}

	private long count(String sql) {
		Long value = jdbcTemplate.queryForObject(sql, Long.class);
		return value == null ? 0 : value;
	}

	private void validateProblem(ProblemUpdateRequest request) {
		if (request.title() == null || request.title().isBlank()) {
			throw new AdminInvalidRequestException("문제 제목을 입력해 주세요.");
		}
		if (request.description() == null || request.inputDescription() == null || request.outputDescription() == null) {
			throw new AdminInvalidRequestException("문제 설명과 입출력 설명을 입력해 주세요.");
		}
		if (request.timeLimitMs() <= 0 || request.memoryLimitMb() <= 0 || request.difficulty() == null) {
			throw new AdminInvalidRequestException("시간, 메모리, 난이도를 확인해 주세요.");
		}
	}

	private void assertUpdated(int updated, String message) {
		if (updated == 0) {
			throw new AdminInvalidRequestException(message);
		}
	}

	private String nullIfBlank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toLocalDateTime();
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp timestamp = rs.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private static Long longValue(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	private static Integer integerValue(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}
}
