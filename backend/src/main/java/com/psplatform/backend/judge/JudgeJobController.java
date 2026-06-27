package com.psplatform.backend.judge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.submission.Submission;
import com.psplatform.backend.submission.SubmissionResponse;

@RestController
@RequestMapping("/api/judge-jobs")
public class JudgeJobController {

	private final JudgeJobService judgeJobService;

	public JudgeJobController(JudgeJobService judgeJobService) {
		this.judgeJobService = judgeJobService;
	}

	@PostMapping("/next")
	public ResponseEntity<JudgeJobAssignment> next() {
		return judgeJobService.findNextAndMarkRunning()
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@PostMapping("/{jobId}/complete")
	public SubmissionResponse complete(
		@PathVariable long jobId,
		@RequestBody JudgeJobCompleteRequest request
	) {
		Submission submission = judgeJobService.complete(jobId, request);
		return SubmissionResponse.from(submission);
	}
}
