package com.psplatform.backend.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.auth.JwtTokenService;
import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemCreateRequest;
import com.psplatform.backend.problem.ProblemDifficulty;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.problem.ProblemTestCaseCreateRequest;
import com.psplatform.backend.problem.ProblemTestCaseRepository;
import com.psplatform.backend.submission.Submission;
import com.psplatform.backend.submission.SubmissionCreateRequest;
import com.psplatform.backend.submission.SubmissionRepository;
import com.psplatform.backend.submission.SubmissionStatus;
import com.psplatform.backend.submission.SubmissionVerdict;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class JudgeJobControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private ProblemTestCaseRepository problemTestCaseRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private JudgeJobRepository judgeJobRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		submissionRepository.deleteAll();
		userRepository.deleteAll();
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
			.andExpect(jsonPath("$.memoryLimitMb").value(128))
			.andExpect(jsonPath("$.testCases[0].input").value("1 2\n"))
			.andExpect(jsonPath("$.testCases[0].expectedOutput").value("3\n"));

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
				  "verdict": "WRONG_ANSWER",
				  "judgeMessage": "wrong answer on test case 1: expected=3, actual=4"
				}
				"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(submission.getId()))
			.andExpect(jsonPath("$.status").value("JUDGED"))
			.andExpect(jsonPath("$.verdict").value("WRONG_ANSWER"));

		Submission judgedSubmission = submissionRepository.findById(submission.getId()).orElseThrow();
		assertThat(judgedSubmission.getStatus()).isEqualTo(SubmissionStatus.JUDGED);
		assertThat(judgedSubmission.getVerdict()).isEqualTo(SubmissionVerdict.WRONG_ANSWER);
		assertThat(judgedSubmission.getJudgeMessage()).contains("wrong answer on test case 1");
		assertThat(judgedSubmission.getJudgedAt()).isNotNull();
	}

	@Test
	void codeRunUsesProvidedTestCasesWithoutCreatingSubmission() throws Exception {
		Problem problem = createProblem();
		String authorization = authorizationFor("alice@example.com", "alice");

		mockMvc.perform(post("/api/problems/{problemId}/runs", problem.getId())
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "language": "java",
					  "sourceCode": "public class Main {}",
					  "testCases": [
					    {
					      "input": "1 2\\n",
					      "expectedOutput": "3\\n"
					    }
					  ]
					}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.jobId").isNumber())
			.andExpect(jsonPath("$.problemId").value(problem.getId()))
			.andExpect(jsonPath("$.status").value("QUEUED"));

		assertThat(submissionRepository.findByProblemId(problem.getId())).isEmpty();

		mockMvc.perform(post("/api/judge-jobs/next"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobKind").value("CODE_RUN"))
			.andExpect(jsonPath("$.submissionId").doesNotExist())
			.andExpect(jsonPath("$.problemId").value(problem.getId()))
			.andExpect(jsonPath("$.testCases[0].input").value("1 2\n"))
			.andExpect(jsonPath("$.testCases[0].expectedOutput").value("3\n"));
	}

	@Test
	void codeRunRequiresAuthentication() throws Exception {
		Problem problem = createProblem();

		mockMvc.perform(post("/api/problems/{problemId}/runs", problem.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "language": "java",
					  "sourceCode": "public class Main {}",
					  "testCases": [
					    {
					      "input": "1 2\\n",
					      "expectedOutput": "3\\n"
					    }
					  ]
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Authorization header is required"));
	}

	@Test
	void codeRunRejectsTooManyRequestsForSameUser() throws Exception {
		Problem problem = createProblem();
		String authorization = authorizationFor("alice@example.com", "alice");
		String body = """
			{
			  "language": "java",
			  "sourceCode": "public class Main {}",
			  "testCases": [
			    {
			      "input": "1 2\\n",
			      "expectedOutput": "3\\n"
			    }
			  ]
			}
			""";

		mockMvc.perform(post("/api/problems/{problemId}/runs", problem.getId())
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/problems/{problemId}/runs", problem.getId())
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.retryAfterSeconds").value(1));
	}

	@Test
	void completeCodeRunStoresCaseResultsOnJudgeJob() throws Exception {
		Problem problem = createProblem();
		CodeRunResponse created = judgeJobRepository.createCodeRun(
			problem.getId(),
			problem.getTimeLimitMs(),
			problem.getMemoryLimitMb(),
			new CodeRunCreateRequest(
				"java",
				"public class Main {}",
				List.of(new JudgeTestCase("1 2\n", "3\n"))
			)
		);
		JudgeJobAssignment assignment = judgeJobRepository.findNextAndMarkRunning().orElseThrow();

		mockMvc.perform(post("/api/judge-jobs/{jobId}/complete", assignment.jobId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "verdict": "ACCEPTED",
					  "judgeMessage": "",
					  "caseResults": [
					    {
					      "index": 1,
					      "verdict": "ACCEPTED",
					      "stdout": "3\\n",
					      "expectedOutput": "3\\n",
					      "message": ""
					    }
					  ]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobId").value(created.jobId()))
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.verdict").value("ACCEPTED"))
			.andExpect(jsonPath("$.caseResults[0].verdict").value("ACCEPTED"));

		mockMvc.perform(get("/api/runs/{jobId}", created.jobId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobId").value(created.jobId()))
			.andExpect(jsonPath("$.caseResults[0].stdout").value("3\n"));
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
		Problem problem = createProblem();
		problemTestCaseRepository.saveAll(
			problem.getId(),
			List.of(new ProblemTestCaseCreateRequest("1 2\n", "3\n"))
		);
		Submission submission = submissionRepository.save(
			problem.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		judgeJobRepository.create(submission.getId());
		return submission;
	}

	private Problem createProblem() {
		return problemRepository.save(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128,
			ProblemDifficulty.NORMAL,
			List.of(),
			List.of()
		));
	}

	private String authorizationFor(String email, String username) {
		User user = userRepository.save(email, username, "hashed-password", "USER");
		return "Bearer " + jwtTokenService.createAccessToken(user);
	}
}
