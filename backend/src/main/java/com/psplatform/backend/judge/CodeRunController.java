package com.psplatform.backend.judge;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.auth.AuthService;
import com.psplatform.backend.user.User;

@RestController
public class CodeRunController {

	private final CodeRunService codeRunService;
	private final AuthService authService;

	public CodeRunController(CodeRunService codeRunService, AuthService authService) {
		this.codeRunService = codeRunService;
		this.authService = authService;
	}

	@PostMapping("/api/problems/{problemId}/runs")
	public ResponseEntity<CodeRunResponse> create(
		@PathVariable long problemId,
		@RequestBody CodeRunCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User user = authService.verifyAccessToken(authorization);
		CodeRunResponse codeRun = codeRunService.create(problemId, request, user.getId());
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
