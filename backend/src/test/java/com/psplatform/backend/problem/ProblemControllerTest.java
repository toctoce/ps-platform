package com.psplatform.backend.problem;

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

@SpringBootTest
@AutoConfigureMockMvc
class ProblemControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProblemRepository problemRepository;

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
					  "timeLimitMs": 1000,
					  "memoryLimitMb": 128
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/problems/[0-9]+")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.title").value("A+B"))
			.andExpect(jsonPath("$.description").value("두 수를 더하는 문제"))
			.andExpect(jsonPath("$.inputDescription").value("정수 A와 B가 주어진다."))
			.andExpect(jsonPath("$.outputDescription").value("A+B를 출력한다."))
			.andExpect(jsonPath("$.timeLimitMs").value(1000))
			.andExpect(jsonPath("$.memoryLimitMb").value(128));
	}

	@Test
	void findAllProblems() throws Exception {
		problemRepository.save(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128
		));

		mockMvc.perform(get("/api/problems"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].title").value("A+B"));
	}

	@Test
	void findProblemById() throws Exception {
		Problem problem = problemRepository.save(new ProblemCreateRequest(
			"A+B",
			"두 수를 더하는 문제",
			"정수 A와 B가 주어진다.",
			"A+B를 출력한다.",
			1000,
			128
		));

		mockMvc.perform(get("/api/problems/{id}", problem.id()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(problem.id()))
			.andExpect(jsonPath("$.title").value("A+B"));
	}

	@Test
	void findProblemByIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/problems/{id}", 999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Problem not found: 999"));
	}
}
