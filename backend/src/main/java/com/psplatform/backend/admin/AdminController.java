package com.psplatform.backend.admin;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
import com.psplatform.backend.problem.ProblemCreateRequest;
import com.psplatform.backend.problem.ProblemExampleCreateRequest;
import com.psplatform.backend.problem.ProblemResponse;
import com.psplatform.backend.problem.ProblemTestCaseCreateRequest;
import com.psplatform.backend.user.User;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final AdminAccessService adminAccessService;
	private final AdminService adminService;

	public AdminController(AdminAccessService adminAccessService, AdminService adminService) {
		this.adminAccessService = adminAccessService;
		this.adminService = adminService;
	}

	@GetMapping("/dashboard")
	public Dashboard dashboard(@RequestHeader(value = "Authorization", required = false) String authorization) {
		requireAdmin(authorization);
		return adminService.dashboard();
	}

	@GetMapping("/problems")
	public List<ProblemSummary> findProblems(
		@RequestParam(defaultValue = "true") boolean includeArchived,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		requireAdmin(authorization);
		return adminService.findProblems(includeArchived);
	}

	@GetMapping("/problems/{problemId}")
	public ProblemDetail findProblem(
		@PathVariable long problemId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		requireAdmin(authorization);
		return adminService.findProblem(problemId);
	}

	@PostMapping("/problems")
	public ResponseEntity<ProblemResponse> createProblem(
		@RequestBody ProblemCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		Problem problem = adminService.createProblem(requireAdmin(authorization), request);
		return ResponseEntity.created(URI.create("/api/admin/problems/" + problem.getId()))
			.body(ProblemResponse.from(problem));
	}

	@PutMapping("/problems/{problemId}")
	public ProblemDetail updateProblem(
		@PathVariable long problemId,
		@RequestBody ProblemUpdateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		return adminService.updateProblem(requireAdmin(authorization), problemId, request);
	}

	@DeleteMapping("/problems/{problemId}")
	public ResponseEntity<Void> archiveProblem(
		@PathVariable long problemId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.archiveProblem(requireAdmin(authorization), problemId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/problems/{problemId}/restore")
	public ResponseEntity<Void> restoreProblem(
		@PathVariable long problemId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.restoreProblem(requireAdmin(authorization), problemId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/problems/{problemId}/test-cases")
	public TestCase addTestCase(
		@PathVariable long problemId,
		@RequestBody ProblemTestCaseCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		return adminService.addTestCase(requireAdmin(authorization), problemId, request);
	}

	@PutMapping("/problems/{problemId}/test-cases/{testCaseId}")
	public ResponseEntity<Void> updateTestCase(
		@PathVariable long problemId,
		@PathVariable long testCaseId,
		@RequestBody ProblemTestCaseCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.updateTestCase(requireAdmin(authorization), problemId, testCaseId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/problems/{problemId}/test-cases/{testCaseId}")
	public ResponseEntity<Void> deleteTestCase(
		@PathVariable long problemId,
		@PathVariable long testCaseId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.deleteTestCase(requireAdmin(authorization), problemId, testCaseId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/problems/{problemId}/examples")
	public Example addExample(
		@PathVariable long problemId,
		@RequestBody ProblemExampleCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		return adminService.addExample(requireAdmin(authorization), problemId, request);
	}

	@PutMapping("/problems/{problemId}/examples/{exampleId}")
	public ResponseEntity<Void> updateExample(
		@PathVariable long problemId,
		@PathVariable long exampleId,
		@RequestBody ProblemExampleCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.updateExample(requireAdmin(authorization), problemId, exampleId, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/problems/{problemId}/examples/{exampleId}")
	public ResponseEntity<Void> deleteExample(
		@PathVariable long problemId,
		@PathVariable long exampleId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.deleteExample(requireAdmin(authorization), problemId, exampleId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/users")
	public List<UserSummary> findUsers(@RequestHeader(value = "Authorization", required = false) String authorization) {
		requireAdmin(authorization);
		return adminService.findUsers();
	}

	@PatchMapping("/users/{userId}")
	public ResponseEntity<Void> updateUser(
		@PathVariable long userId,
		@RequestBody UserUpdateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.updateUser(requireAdmin(authorization), userId, request);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/submissions")
	public List<SubmissionSummary> findSubmissions(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		requireAdmin(authorization);
		return adminService.findSubmissions();
	}

	@GetMapping("/judge-jobs")
	public List<JudgeJobSummary> findJudgeJobs(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		requireAdmin(authorization);
		return adminService.findJudgeJobs();
	}

	@PostMapping("/judge-jobs/{jobId}/requeue")
	public ResponseEntity<Void> requeueJudgeJob(
		@PathVariable long jobId,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.requeueJudgeJob(requireAdmin(authorization), jobId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/categories")
	public List<CategorySummary> findCategories(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		requireAdmin(authorization);
		return adminService.findCategories();
	}

	@PostMapping("/categories")
	public ResponseEntity<Void> createCategory(
		@RequestBody CategoryUpdateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.createCategory(requireAdmin(authorization), request);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/categories/{categoryId}")
	public ResponseEntity<Void> updateCategory(
		@PathVariable long categoryId,
		@RequestBody CategoryUpdateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		adminService.updateCategory(requireAdmin(authorization), categoryId, request);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/audit-logs")
	public List<AuditLog> findAuditLogs(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		requireAdmin(authorization);
		return adminService.findAuditLogs();
	}

	private User requireAdmin(String authorization) {
		return adminAccessService.requireAdmin(authorization);
	}
}
