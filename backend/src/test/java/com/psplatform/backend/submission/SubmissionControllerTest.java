package com.psplatform.backend.submission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@BeforeEach
	void setUp() {
		submissionRepository.deleteAll();
		problemRepository.deleteAll();
	}

	@Test
	void createSubmissionCreatesQueuedJudgeJob() throws Exception {
		Problem problem = createProblem();

		mockMvc.perform(post("/api/problems/{problemId}/submissions", problem.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "language": "java",
					  "sourceCode": "public class Main {}"
					}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/submissions/[0-9]+")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.problemId").value(problem.getId()))
			.andExpect(jsonPath("$.language").value("java"))
			.andExpect(jsonPath("$.sourceCode").value("public class Main {}"))
			.andExpect(jsonPath("$.status").value("PENDING"))
			.andExpect(jsonPath("$.verdict").doesNotExist());

		Submission submission = submissionRepository.findByProblemId(problem.getId()).getFirst();
		org.assertj.core.api.Assertions.assertThat(
			submissionRepository.countJudgeJobsBySubmissionId(submission.getId())
		).isEqualTo(1);
	}

	@Test
	void createSubmissionReturnsNotFoundWhenProblemDoesNotExist() throws Exception {
		mockMvc.perform(post("/api/problems/{problemId}/submissions", 999)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "language": "java",
					  "sourceCode": "public class Main {}"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Problem not found: 999"));
	}

	@Test
	void findSubmissionsByProblemId() throws Exception {
		Problem problem = createProblem();
		submissionRepository.save(problem.getId(), new SubmissionCreateRequest("java", "public class Main {}"));

		mockMvc.perform(get("/api/problems/{problemId}/submissions", problem.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].problemId").value(problem.getId()))
			.andExpect(jsonPath("$[0].status").value("PENDING"));
	}

	@Test
	void findSubmissionById() throws Exception {
		Problem problem = createProblem();
		Submission submission = submissionRepository.save(
			problem.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);

		mockMvc.perform(get("/api/submissions/{id}", submission.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(submission.getId()))
			.andExpect(jsonPath("$.problemId").value(problem.getId()));
	}

	@Test
	void findSubmissionByIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/submissions/{id}", 999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Submission not found: 999"));
	}

	@Test
	void submissionVerdictsAreDefined() {
		org.assertj.core.api.Assertions.assertThat(SubmissionVerdict.values())
			.containsExactly(
				SubmissionVerdict.ACCEPTED,
				SubmissionVerdict.WRONG_ANSWER,
				SubmissionVerdict.TIME_LIMIT_EXCEEDED,
				SubmissionVerdict.MEMORY_LIMIT_EXCEEDED,
				SubmissionVerdict.RUNTIME_ERROR,
				SubmissionVerdict.COMPILE_ERROR
			);
	}

	private Problem createProblem() {
		return problemRepository.save(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128
		));
	}
}
