package com.psplatform.backend.submission;

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
public class SubmissionController {

	private final SubmissionService submissionService;

	public SubmissionController(SubmissionService submissionService) {
		this.submissionService = submissionService;
	}

	@PostMapping("/api/problems/{problemId}/submissions")
	public ResponseEntity<SubmissionResponse> create(
		@PathVariable long problemId,
		@RequestBody SubmissionCreateRequest request
	) {
		Submission submission = submissionService.create(problemId, request);
		return ResponseEntity
			.accepted()
			.location(URI.create("/api/submissions/" + submission.getId()))
			.body(SubmissionResponse.from(submission));
	}

	@GetMapping("/api/problems/{problemId}/submissions")
	public List<SubmissionResponse> findByProblemId(@PathVariable long problemId) {
		return submissionService.findByProblemId(problemId)
			.stream()
			.map(SubmissionResponse::from)
			.toList();
	}

	@GetMapping("/api/submissions/{id}")
	public SubmissionResponse findById(@PathVariable long id) {
		return SubmissionResponse.from(submissionService.findById(id));
	}
}
