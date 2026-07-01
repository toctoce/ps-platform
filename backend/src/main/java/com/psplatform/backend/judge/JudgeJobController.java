package com.psplatform.backend.judge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	public Object complete(
		@PathVariable long jobId,
		@RequestBody JudgeJobCompleteRequest request
	) {
		JudgeJobCompleteResult result = judgeJobService.complete(jobId, request);
		if (result.jobKind() == JudgeJobKind.CODE_RUN) {
			return result.codeRun();
		}
		return SubmissionResponse.from(result.submission());
	}

	@PostMapping("/requeue-stale")
	public JudgeJobRequeueStaleResponse requeueStale(@RequestBody JudgeJobRequeueStaleRequest request) {
		return judgeJobService.requeueStale(request);
	}
}
