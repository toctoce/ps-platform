package com.psplatform.backend.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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

import com.psplatform.backend.auth.JwtTokenService;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

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
	private ProblemExampleRepository problemExampleRepository;

	@Autowired
	private ProblemService problemService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		problemRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void createProblemNormalizesPlainSolutionToMarkdown() throws Exception {
		mockMvc.perform(post("/api/problems")
				.header("Authorization", adminAuthorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "B+A",
					  "description": "두 수를 더하는 또 다른 문제",
					  "solution": "두 수를 더하고 결과를 출력합니다.",
					  "inputDescription": "정수 A와 B가 주어진다.",
					  "outputDescription": "A+B를 출력한다.",
					  "difficulty": "NORMAL",
					  "categories": ["수학"],
					  "timeLimitMs": 1000,
					  "memoryLimitMb": 128
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.solution").value("## 해설\n\n두 수를 더하고 결과를 출력합니다."));
	}

	@Test
	void createProblemDecodesEscapedNewlinesFromRequest() throws Exception {
		mockMvc.perform(post("/api/problems")
				.header("Authorization", adminAuthorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "EscapedNewlineTest",
					  "description": "백슬래시 문자 처리 테스트",
					  "solution": "## 접근\\\\n\\\\n`A+B`는 두 수의 합을 구합니다.\\\\n```java\\\\npublic class Main {}\\\\n```",
					  "inputDescription": "두 정수 A, B",
					  "outputDescription": "A + B",
					  "difficulty": "NORMAL",
					  "categories": ["구현"],
					  "timeLimitMs": 1000,
					  "memoryLimitMb": 128
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.solution", containsString("## 접근")))
			.andExpect(jsonPath("$.solution", not(containsString("\\\\n"))));
	}

	@Test
	void createProblem() throws Exception {
		mockMvc.perform(post("/api/problems")
				.header("Authorization", adminAuthorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "A+B",
					  "description": "두 수를 더하는 문제",
					  "solution": "## 풀이\\n두 수를 읽고 더합니다.",
					  "inputDescription": "정수 A와 B가 주어진다.",
					  "inputConstraints": "1 <= A, B <= 10000",
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
					  ],
					  "examples": [
					    {
					      "input": "1 2\\n",
					      "expectedOutput": "3\\n",
					      "description": "1과 2를 더하면 3입니다."
					    }
					  ]
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/problems/[0-9]+")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.title").value("A+B"))
			.andExpect(jsonPath("$.description").value("두 수를 더하는 문제"))
			.andExpect(jsonPath("$.solution").value("## 풀이\n두 수를 읽고 더합니다."))
			.andExpect(jsonPath("$.inputDescription").value("정수 A와 B가 주어진다."))
			.andExpect(jsonPath("$.inputConstraints").value("1 <= A, B <= 10000"))
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
		List<ProblemExample> examples = problemExampleRepository.findByProblemId(problem.getId());
		assertThat(examples).hasSize(1);
		assertThat(examples.getFirst().getInput()).isEqualTo("1 2\n");
		assertThat(examples.getFirst().getExpectedOutput()).isEqualTo("3\n");
		assertThat(examples.getFirst().getDescription()).isEqualTo("1과 2를 더하면 3입니다.");
	}

	@Test
	void createProblemRejectsDuplicateTrimmedTitle() throws Exception {
		problemService.create(new ProblemCreateRequest(
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

		mockMvc.perform(post("/api/problems")
				.header("Authorization", adminAuthorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "  A+B  ",
					  "description": "중복 제목 문제",
					  "inputDescription": "두 정수가 주어진다.",
					  "outputDescription": "합을 출력한다.",
					  "difficulty": "EASY",
					  "timeLimitMs": 1000,
					  "memoryLimitMb": 128
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Problem title already exists: A+B"));
	}

	@Test
	void findAllProblems() throws Exception {
		problemRepository.save(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"## 풀이\n두 수를 읽고 더합니다.",
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
				"## 풀이\n두 수를 읽고 더합니다.",
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
			.andExpect(jsonPath("$.solution").value("## 풀이\n두 수를 읽고 더합니다."))
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
	void findCategories() throws Exception {
		problemService.create(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128,
			ProblemDifficulty.NORMAL,
			List.of("수학", "구현"),
			List.of()
		));
		problemService.create(new ProblemCreateRequest(
			"문자열 출력",
			"문자열을 그대로 출력하는 문제",
			"문자열이 주어진다.",
			"문자열을 출력한다.",
			1000,
			128,
			ProblemDifficulty.EASY,
			List.of("문자열", "구현"),
			List.of()
		));

		mockMvc.perform(get("/api/categories"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.name == '구현')].problemCount").value(hasItem(2)))
			.andExpect(jsonPath("$[?(@.name == '수학')].problemCount").value(hasItem(1)))
			.andExpect(jsonPath("$[?(@.name == '문자열')].problemCount").value(hasItem(1)));
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
				.header("Authorization", adminAuthorization())
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
	void addExampleToProblem() throws Exception {
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

		mockMvc.perform(post("/api/problems/{id}/examples", problem.getId())
				.header("Authorization", adminAuthorization())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "input": "10 20\\n",
					  "expectedOutput": "30\\n",
					  "description": "10과 20을 더하면 30입니다."
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string(
				"Location",
				org.hamcrest.Matchers.matchesPattern("/api/problems/[0-9]+/examples/[0-9]+")
			))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.problemId").value(problem.getId()))
			.andExpect(jsonPath("$.input").value("10 20\n"))
			.andExpect(jsonPath("$.expectedOutput").value("30\n"))
			.andExpect(jsonPath("$.description").value("10과 20을 더하면 30입니다."))
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
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/problems/{id}/test-cases", problem.getId())
				.header("Authorization", adminAuthorization()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].input").value("1 2\n"))
			.andExpect(jsonPath("$[0].expectedOutput").value("3\n"))
			.andExpect(jsonPath("$[0].sortOrder").value(0))
			.andExpect(jsonPath("$[1].input").value("10 20\n"))
			.andExpect(jsonPath("$[1].expectedOutput").value("30\n"))
			.andExpect(jsonPath("$[1].sortOrder").value(1));
	}

	@Test
	void findExamplesByProblemId() throws Exception {
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
		problemExampleRepository.saveAll(
			problem.getId(),
			List.of(
				new ProblemExampleCreateRequest("1 2\n", "3\n", "1과 2를 더하면 3입니다."),
				new ProblemExampleCreateRequest("10 20\n", "30\n", null)
			)
		);

		mockMvc.perform(get("/api/problems/{id}/examples", problem.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].input").value("1 2\n"))
			.andExpect(jsonPath("$[0].expectedOutput").value("3\n"))
			.andExpect(jsonPath("$[0].description").value("1과 2를 더하면 3입니다."))
			.andExpect(jsonPath("$[0].sortOrder").value(0))
			.andExpect(jsonPath("$[1].input").value("10 20\n"))
			.andExpect(jsonPath("$[1].expectedOutput").value("30\n"))
			.andExpect(jsonPath("$[1].description").doesNotExist())
			.andExpect(jsonPath("$[1].sortOrder").value(1));
	}

	@Test
	void findProblemByIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/problems/{id}", 999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Problem not found: 999"));
	}

	private String adminAuthorization() {
		User user = userRepository.findByEmail("admin@example.com")
			.orElseGet(() -> userRepository.save("admin@example.com", "admin", "password", "ADMIN"));
		return "Bearer " + jwtTokenService.createAccessToken(user);
	}
}
