package com.psplatform.backend.report;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.auth.AuthService;
import com.psplatform.backend.admin.AdminService;
import com.psplatform.backend.user.User;

@RestController
public class ProblemReportController {

	private final ProblemReportService problemReportService;
	private final AuthService authService;
	private final AdminService adminService;

	public ProblemReportController(
		ProblemReportService problemReportService,
		AuthService authService,
		AdminService adminService
	) {
		this.problemReportService = problemReportService;
		this.authService = authService;
		this.adminService = adminService;
	}

	@PostMapping("/api/problems/{problemId}/reports")
	public ResponseEntity<ProblemReportResponse> create(
		@PathVariable long problemId,
		@RequestBody ProblemReportCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User user = authService.verifyAccessToken(authorization);
		ProblemReport report = problemReportService.create(problemId, user, request);
		return ResponseEntity
			.created(URI.create("/api/admin/problem-reports/" + report.getId()))
			.body(ProblemReportResponse.from(report));
	}

	@GetMapping("/api/admin/problem-reports")
	public List<ProblemReportResponse> findAll(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User user = authService.verifyAccessToken(authorization);
		return problemReportService.findAll(user)
			.stream()
			.map(ProblemReportResponse::from)
			.toList();
	}

	@PatchMapping("/api/admin/problem-reports/{reportId}")
	public ProblemReportResponse review(
		@PathVariable long reportId,
		@RequestBody ProblemReportReviewRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User user = authService.verifyAccessToken(authorization);
		ProblemReport reviewed = problemReportService.review(reportId, user, request);
		adminService.audit(user, "REPORT_REVIEW", "PROBLEM_REPORT", Long.toString(reportId), request.status().name());
		return ProblemReportResponse.from(reviewed);
	}
}
