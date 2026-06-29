package com.psplatform.backend.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

@SpringBootTest
@AutoConfigureMockMvc
class ProblemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private ProblemTestCaseRepository problemTestCaseRepository;

	@Autowired
	private ProblemService problemService;

	@BeforeEach
	void setUp() {
		problemRepository.deleteAll();
	}

	@Test
	void createProblem() throws Exception {
		mockMvc.perform(post("/api/problems")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "A+B",
					  "description": "두 수를 더하는 문제",
					  "inputDescription": "정수 A와 B가 주어진다.",
					  "outputDescription": "A+B를 출력한다.",
					  "difficulty": "HARD",
					  "categories": ["수학", "입출력"],
					  "timeLimitMs": 1000,
					  "memoryLimitMb": 128,
					  "testCases": [
					    {
					      "input": "1 2\\n",
					      "expectedOutput": "3\\n"
					    }
					  ]
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/problems/[0-9]+")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.title").value("A+B"))
			.andExpect(jsonPath("$.description").value("두 수를 더하는 문제"))
			.andExpect(jsonPath("$.inputDescription").value("정수 A와 B가 주어진다."))
			.andExpect(jsonPath("$.outputDescription").value("A+B를 출력한다."))
			.andExpect(jsonPath("$.difficulty").value("HARD"))
			.andExpect(jsonPath("$.categories[*].name", hasItems("수학", "입출력")))
			.andExpect(jsonPath("$.timeLimitMs").value(1000))
			.andExpect(jsonPath("$.memoryLimitMb").value(128));

		Problem problem = problemRepository.findAll().getFirst();
		List<ProblemTestCase> testCases = problemTestCaseRepository.findByProblemId(problem.getId());
		assertThat(testCases).hasSize(1);
		assertThat(testCases.getFirst().getInput()).isEqualTo("1 2\n");
		assertThat(testCases.getFirst().getExpectedOutput()).isEqualTo("3\n");
	}

	@Test
	void findAllProblems() throws Exception {
		problemRepository.save(new ProblemCreateRequest(
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

		mockMvc.perform(get("/api/problems"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("A+B"));
	}

	@Test
	void findProblemById() throws Exception {
		Problem problem = problemService.create(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128,
			ProblemDifficulty.NORMAL,
			List.of("배열"),
			List.of()
		));

		mockMvc.perform(get("/api/problems/{id}", problem.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(problem.getId()))
			.andExpect(jsonPath("$.title").value("A+B"))
			.andExpect(jsonPath("$.categories[0].name").value("배열"));
	}

	@Test
	void findProblemsByCategory() throws Exception {
		problemService.create(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128,
			ProblemDifficulty.NORMAL,
			List.of("구현"),
			List.of()
		));

		mockMvc.perform(get("/api/problems?category={category}", "구현"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("A+B"))
			.andExpect(jsonPath("$[0].categories[0].name").value("구현"));
	}

	@Test
	void findRandomProblems() throws Exception {
		problemRepository.save(new ProblemCreateRequest(
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

		mockMvc.perform(get("/api/problems/random?count=1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].title").value("A+B"));
	}

	@Test
	void addTestCaseToProblem() throws Exception {
		Problem problem = problemRepository.save(new ProblemCreateRequest(
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

		mockMvc.perform(post("/api/problems/{id}/test-cases", problem.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "input": "10 20\\n",
					  "expectedOutput": "30\\n"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string(
				"Location",
				org.hamcrest.Matchers.matchesPattern("/api/problems/[0-9]+/test-cases/[0-9]+")
			))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.problemId").value(problem.getId()))
			.andExpect(jsonPath("$.input").value("10 20\n"))
			.andExpect(jsonPath("$.expectedOutput").value("30\n"))
			.andExpect(jsonPath("$.sortOrder").value(0));
	}

	@Test
	void findTestCasesByProblemId() throws Exception {
		Problem problem = problemRepository.save(new ProblemCreateRequest(
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
		problemTestCaseRepository.saveAll(
			problem.getId(),
			List.of(
				new ProblemTestCaseCreateRequest("1 2\n", "3\n"),
				new ProblemTestCaseCreateRequest("10 20\n", "30\n")
			)
		);

		mockMvc.perform(get("/api/problems/{id}/test-cases", problem.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].input").value("1 2\n"))
			.andExpect(jsonPath("$[0].expectedOutput").value("3\n"))
			.andExpect(jsonPath("$[0].sortOrder").value(0))
			.andExpect(jsonPath("$[1].input").value("10 20\n"))
			.andExpect(jsonPath("$[1].expectedOutput").value("30\n"))
			.andExpect(jsonPath("$[1].sortOrder").value(1));
	}

	@Test
	void findProblemByIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/problems/{id}", 999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Problem not found: 999"));
	}
}
