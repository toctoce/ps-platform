package com.psplatform.backend.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.auth.JwtTokenService;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("DELETE FROM problem_reports");
		problemRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void adminApisRejectRegularUser() throws Exception {
		String authorization = authorizationFor("user@example.com", "user", "USER");

		mockMvc.perform(get("/api/admin/dashboard").header("Authorization", authorization))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("관리자 권한이 필요합니다."));
	}

	@Test
	void adminManagesProblemAndHiddenJudgeCases() throws Exception {
		String authorization = authorizationFor("admin@example.com", "admin", "ADMIN");

		String location = mockMvc.perform(post("/api/admin/problems")
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "관리자 문제",
					  "description": "설명",
					  "solution": "해설",
					  "inputDescription": "입력",
					  "inputConstraints": "1 <= N <= 10",
					  "outputDescription": "출력",
					  "timeLimitMs": 1000,
					  "memoryLimitMb": 128,
					  "difficulty": "NORMAL",
					  "categories": ["구현"],
					  "examples": [],
					  "testCases": []
					}
					"""))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getHeader("Location");
		long problemId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));

		mockMvc.perform(post("/api/admin/problems/{problemId}/test-cases", problemId)
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"input":"1\\n","expectedOutput":"1\\n"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.input").value("1\n"));

		mockMvc.perform(put("/api/admin/problems/{problemId}", problemId)
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "title": "수정한 관리자 문제",
					  "description": "수정 설명",
					  "solution": "수정 해설",
					  "inputDescription": "입력",
					  "inputConstraints": "1 <= N <= 20",
					  "outputDescription": "출력",
					  "timeLimitMs": 2000,
					  "memoryLimitMb": 256,
					  "difficulty": "HARD",
					  "categories": ["구현", "자료구조"]
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("수정한 관리자 문제"))
			.andExpect(jsonPath("$.testCases[0].expectedOutput").value("1\n"));

		mockMvc.perform(delete("/api/admin/problems/{problemId}", problemId)
				.header("Authorization", authorization))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/problems/{problemId}", problemId))
			.andExpect(status().isNotFound());

		mockMvc.perform(get("/api/admin/audit-logs").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].action").value("PROBLEM_ARCHIVE"));
	}

	@Test
	void adminSuspendsUserAndRevokesAccess() throws Exception {
		String adminAuthorization = authorizationFor("admin@example.com", "admin", "ADMIN");
		User user = userRepository.save("member@example.com", "member", "password", "USER");
		String userAuthorization = "Bearer " + jwtTokenService.createAccessToken(user);

		mockMvc.perform(patch("/api/admin/users/{userId}", user.getId())
				.header("Authorization", adminAuthorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role":"USER","suspended":true}
					"""))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").header("Authorization", userAuthorization))
			.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/admin/users").header("Authorization", adminAuthorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[?(@.id == %s)].suspendedAt".formatted(user.getId())).exists());
	}

	private String authorizationFor(String email, String username, String role) {
		User user = userRepository.save(email, username, "hashed-password", role);
		return "Bearer " + jwtTokenService.createAccessToken(user);
	}
}
