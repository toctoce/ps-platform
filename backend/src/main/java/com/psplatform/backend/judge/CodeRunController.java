package com.psplatform.backend.judge;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CodeRunController {

	private final CodeRunService codeRunService;

	public CodeRunController(CodeRunService codeRunService) {
		this.codeRunService = codeRunService;
	}

	@PostMapping("/api/problems/{problemId}/runs")
	public ResponseEntity<CodeRunResponse> create(
		@PathVariable long problemId,
		@RequestBody CodeRunCreateRequest request
	) {
		CodeRunResponse codeRun = codeRunService.create(problemId, request);
		return ResponseEntity
			.accepted()
			.location(URI.create("/api/runs/" + codeRun.jobId()))
			.body(codeRun);
	}

	@GetMapping("/api/runs/{jobId}")
	public CodeRunResponse findByJobId(@PathVariable long jobId) {
		return codeRunService.findByJobId(jobId);
	}
}
