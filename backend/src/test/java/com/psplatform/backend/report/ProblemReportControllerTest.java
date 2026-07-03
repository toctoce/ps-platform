package com.psplatform.backend.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemCreateRequest;
import com.psplatform.backend.problem.ProblemDifficulty;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class ProblemReportControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private ProblemReportRepository problemReportRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		problemReportRepository.deleteAll();
		userRepository.deleteAll();
		problemRepository.deleteAll();
	}

	@Test
	void createProblemReport() throws Exception {
		Problem problem = createProblem();
		String authorization = authorizationFor("alice@example.com", "alice", "USER");

		mockMvc.perform(post("/api/problems/{problemId}/reports", problem.getId())
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "type": "DESCRIPTION_ERROR",
					  "message": "문제 설명의 조건이 모호합니다."
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/admin/problem-reports/[0-9]+")))
			.andExpect(jsonPath("$.problemId").value(problem.getId()))
			.andExpect(jsonPath("$.problemTitle").value("A+B"))
			.andExpect(jsonPath("$.reporterUsername").value("alice"))
			.andExpect(jsonPath("$.type").value("DESCRIPTION_ERROR"))
			.andExpect(jsonPath("$.message").value("문제 설명의 조건이 모호합니다."))
			.andExpect(jsonPath("$.status").value("PENDING"));
	}

	@Test
	void createProblemReportRequiresAuthentication() throws Exception {
		Problem problem = createProblem();

		mockMvc.perform(post("/api/problems/{problemId}/reports", problem.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "type": "DESCRIPTION_ERROR",
					  "message": "문제 설명의 조건이 모호합니다."
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Authorization header is required"));
	}

	@Test
	void adminFindsAndReviewsProblemReports() throws Exception {
		Problem problem = createProblem();
		User reporter = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");
		ProblemReport report = problemReportRepository.save(
			problem.getId(),
			reporter.getId(),
			new ProblemReportCreateRequest(ProblemReportType.TEST_CASE_ERROR, "테스트케이스가 잘못된 것 같습니다.")
		);
		String adminAuthorization = authorizationFor("admin@example.com", "admin", "ADMIN");

		mockMvc.perform(get("/api/admin/problem-reports")
				.header("Authorization", adminAuthorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(report.getId()))
			.andExpect(jsonPath("$[0].type").value("TEST_CASE_ERROR"));

		mockMvc.perform(patch("/api/admin/problem-reports/{reportId}", report.getId())
				.header("Authorization", adminAuthorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "status": "RESOLVED",
					  "adminNote": "테스트케이스를 수정했습니다."
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("RESOLVED"))
			.andExpect(jsonPath("$.adminNote").value("테스트케이스를 수정했습니다."))
			.andExpect(jsonPath("$.reviewedAt").exists());
	}

	@Test
	void adminProblemReportApisRequireAdminRole() throws Exception {
		String authorization = authorizationFor("alice@example.com", "alice", "USER");

		mockMvc.perform(get("/api/admin/problem-reports")
				.header("Authorization", authorization))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value("Admin role is required"));
	}

	private Problem createProblem() {
		return problemRepository.save(new ProblemCreateRequest(
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
	}

	private String authorizationFor(String email, String username, String role) {
		User user = userRepository.save(email, username, "hashed-password", role);
		return "Bearer " + jwtTokenService.createAccessToken(user);
	}
}
