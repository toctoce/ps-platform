package com.psplatform.backend.problem;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/problems")
public class ProblemController {

	private final ProblemService problemService;

	public ProblemController(ProblemService problemService) {
		this.problemService = problemService;
	}

	@PostMapping
	public ResponseEntity<ProblemResponse> create(@RequestBody ProblemCreateRequest request) {
		Problem problem = problemService.create(request);
		return ResponseEntity
			.created(URI.create("/api/problems/" + problem.getId()))
			.body(ProblemResponse.from(problem));
	}

	@GetMapping
	public List<ProblemResponse> findAll() {
		return problemService.findAll()
			.stream()
			.map(ProblemResponse::from)
			.toList();
	}

	@GetMapping("/{id}")
	public ProblemResponse findById(@PathVariable long id) {
		return ProblemResponse.from(problemService.findById(id));
	}

	@GetMapping("/{id}/test-cases")
	public List<ProblemTestCaseResponse> findTestCases(@PathVariable long id) {
		return problemService.findTestCasesByProblemId(id)
			.stream()
			.map(ProblemTestCaseResponse::from)
			.toList();
	}

	@PostMapping("/{id}/test-cases")
	public ResponseEntity<ProblemTestCaseResponse> addTestCase(
		@PathVariable long id,
		@RequestBody ProblemTestCaseCreateRequest request
	) {
		ProblemTestCase testCase = problemService.addTestCase(id, request);
		return ResponseEntity
			.created(URI.create("/api/problems/" + id + "/test-cases/" + testCase.getId()))
			.body(ProblemTestCaseResponse.from(testCase));
	}
}
