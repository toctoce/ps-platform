package com.psplatform.backend.problem;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.admin.AdminAccessService;
import com.psplatform.backend.admin.AdminService;
import com.psplatform.backend.user.User;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

	private final ProblemService problemService;
	private final AdminAccessService adminAccessService;
	private final AdminService adminService;

	public ProblemController(
		ProblemService problemService,
		AdminAccessService adminAccessService,
		AdminService adminService
	) {
		this.problemService = problemService;
		this.adminAccessService = adminAccessService;
		this.adminService = adminService;
	}

	@PostMapping
	public ResponseEntity<ProblemResponse> create(
		@RequestBody ProblemCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		Problem problem = adminService.createProblem(requireAdmin(authorization), request);
		return ResponseEntity
			.created(URI.create("/api/problems/" + problem.getId()))
			.body(ProblemResponse.from(problem));
	}

	@GetMapping
	public List<ProblemResponse> findAll(@RequestParam(required = false) String category) {
		return problemService.findAll(category)
			.stream()
			.map(ProblemResponse::from)
			.toList();
	}

	@GetMapping("/{id}")
	public ProblemResponse findById(@PathVariable long id) {
		return ProblemResponse.from(problemService.findById(id));
	}

	@GetMapping("/{id}/test-cases")
	public List<ProblemTestCaseResponse> findTestCases(
		@PathVariable long id,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		requireAdmin(authorization);
		return problemService.findTestCasesByProblemId(id)
			.stream()
			.map(ProblemTestCaseResponse::from)
			.toList();
	}

	@GetMapping("/{id}/examples")
	public List<ProblemExampleResponse> findExamples(@PathVariable long id) {
		return problemService.findExamplesByProblemId(id)
			.stream()
			.map(ProblemExampleResponse::from)
			.toList();
	}

	@GetMapping("/random")
	public List<ProblemResponse> findRandomProblems(@RequestParam(defaultValue = "5") int count) {
		return problemService.findRandomProblems(count)
			.stream()
			.map(ProblemResponse::from)
			.toList();
	}

	@PostMapping("/{id}/test-cases")
	public ResponseEntity<ProblemTestCaseResponse> addTestCase(
		@PathVariable long id,
		@RequestBody ProblemTestCaseCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User admin = requireAdmin(authorization);
		var saved = adminService.addTestCase(admin, id, request);
		return ResponseEntity
			.created(URI.create("/api/problems/" + id + "/test-cases/" + saved.id()))
			.body(new ProblemTestCaseResponse(saved.id(), id, saved.input(), saved.expectedOutput(), saved.sortOrder()));
	}

	@PostMapping("/{id}/examples")
	public ResponseEntity<ProblemExampleResponse> addExample(
		@PathVariable long id,
		@RequestBody ProblemExampleCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User admin = requireAdmin(authorization);
		var saved = adminService.addExample(admin, id, request);
		return ResponseEntity
			.created(URI.create("/api/problems/" + id + "/examples/" + saved.id()))
			.body(new ProblemExampleResponse(
				saved.id(), id, saved.input(), saved.expectedOutput(), saved.description(), saved.sortOrder()
			));
	}

	private User requireAdmin(String authorization) {
		return adminAccessService.requireAdmin(authorization);
	}
}
