package com.psplatform.backend.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemCreateRequest;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.submission.Submission;
import com.psplatform.backend.submission.SubmissionCreateRequest;
import com.psplatform.backend.submission.SubmissionRepository;
import com.psplatform.backend.submission.SubmissionStatus;
import com.psplatform.backend.submission.SubmissionVerdict;

@SpringBootTest
@AutoConfigureMockMvc
class JudgeJobControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private JudgeJobRepository judgeJobRepository;

	@BeforeEach
	void setUp() {
		submissionRepository.deleteAll();
		problemRepository.deleteAll();
	}

	@Test
	void nextReturnsNoContentWhenQueueIsEmpty() throws Exception {
		mockMvc.perform(post("/api/judge-jobs/next"))
			.andExpect(status().isNoContent());
	}

	@Test
	void nextReturnsQueuedJobAndMarksSubmissionRunning() throws Exception {
		Submission submission = createSubmissionWithJob();

		mockMvc.perform(post("/api/judge-jobs/next"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobId").isNumber())
			.andExpect(jsonPath("$.submissionId").value(submission.getId()))
			.andExpect(jsonPath("$.problemId").value(submission.getProblemId()))
			.andExpect(jsonPath("$.language").value("java"))
			.andExpect(jsonPath("$.sourceCode").value("public class Main {}"))
			.andExpect(jsonPath("$.timeLimitMs").value(1000))
			.andExpect(jsonPath("$.memoryLimitMb").value(128));

		Submission runningSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
		assertThat(runningSubmission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);
	}

	@Test
	void completeMarksWrongAnswerAsJudged() throws Exception {
		Submission submission = createSubmissionWithJob();
		JudgeJobAssignment assignment = judgeJobRepository.findNextAndMarkRunning().orElseThrow();

		mockMvc.perform(post("/api/judge-jobs/{jobId}/complete", assignment.jobId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "verdict": "WRONG_ANSWER"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(submission.getId()))
			.andExpect(jsonPath("$.status").value("JUDGED"))
			.andExpect(jsonPath("$.verdict").value("WRONG_ANSWER"));

		Submission judgedSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
		assertThat(judgedSubmission.getStatus()).isEqualTo(SubmissionStatus.JUDGED);
		assertThat(judgedSubmission.getVerdict()).isEqualTo(SubmissionVerdict.WRONG_ANSWER);
		assertThat(judgedSubmission.getJudgedAt()).isNotNull();
	}

	@Test
	void completeReturnsNotFoundWhenJobIsNotRunning() throws Exception {
		mockMvc.perform(post("/api/judge-jobs/{jobId}/complete", 999)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "verdict": "ACCEPTED"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Judge job not found: 999"));
	}

	@Test
	void requeueStaleReturnsRunningJobToQueue() throws Exception {
		Submission submission = createSubmissionWithJob();
		judgeJobRepository.findNextAndMarkRunning().orElseThrow();

		mockMvc.perform(post("/api/judge-jobs/requeue-stale")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "staleAfterSeconds": 0
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requeuedCount").value(1));

		Submission pendingSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
		assertThat(pendingSubmission.getStatus()).isEqualTo(SubmissionStatus.PENDING);

		mockMvc.perform(post("/api/judge-jobs/next"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.submissionId").value(submission.getId()));
	}

	@Test
	void requeueStaleKeepsFreshRunningJob() throws Exception {
		Submission submission = createSubmissionWithJob();
		judgeJobRepository.findNextAndMarkRunning().orElseThrow();

		mockMvc.perform(post("/api/judge-jobs/requeue-stale")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "staleAfterSeconds": 3600
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.requeuedCount").value(0));

		Submission runningSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
		assertThat(runningSubmission.getStatus()).isEqualTo(SubmissionStatus.RUNNING);

		mockMvc.perform(post("/api/judge-jobs/next"))
			.andExpect(status().isNoContent());
	}

	private Submission createSubmissionWithJob() {
		Problem problem = problemRepository.save(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128
		));
		Submission submission = submissionRepository.save(
			problem.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		judgeJobRepository.create(submission.getId());
		return submission;
	}
}
