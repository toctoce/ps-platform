package com.psplatform.backend.submission;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.auth.JwtTokenService;
import com.psplatform.backend.problem.Problem;
import com.psplatform.backend.problem.ProblemCreateRequest;
import com.psplatform.backend.problem.ProblemCategoryRepository;
import com.psplatform.backend.problem.ProblemDifficulty;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.judge.JudgeJobRepository;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ProblemRepository problemRepository;

	@Autowired
	private ProblemCategoryRepository problemCategoryRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	@Autowired
	private JudgeJobRepository judgeJobRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService jwtTokenService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private GuestSessionService guestSessionService;

	@BeforeEach
	void setUp() {
		submissionRepository.deleteAll();
		userRepository.deleteAll();
		problemRepository.deleteAll();
	}

	@Test
	void createSubmissionCreatesQueuedJudgeJob() throws Exception {
		Problem problem = createProblem();
		String authorization = authorizationFor("alice@example.com", "alice");

		mockMvc.perform(post("/api/problems/{problemId}/submissions", problem.getId())
				.header("Authorization", authorization)
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
			.andExpect(jsonPath("$.verdict").doesNotExist())
			.andExpect(jsonPath("$.createdAt", org.hamcrest.Matchers.endsWith("Z")))
			.andExpect(header().doesNotExist("Set-Cookie"))
			.andExpect(header().doesNotExist("X-Guest-Submissions-Remaining"));

		Submission submission = submissionRepository.findByProblemId(problem.getId()).getFirst();
		org.assertj.core.api.Assertions.assertThat(submission.getCreatedAt())
			.isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5, ChronoUnit.SECONDS));
		org.assertj.core.api.Assertions.assertThat(
			judgeJobRepository.countBySubmissionId(submission.getId())
		).isEqualTo(1);
	}

	@Test
	void createSubmissionReturnsNotFoundWhenProblemDoesNotExist() throws Exception {
		String authorization = authorizationFor("alice@example.com", "alice");

		mockMvc.perform(post("/api/problems/{problemId}/submissions", 999)
				.header("Authorization", authorization)
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
	void createGuestSubmissionSetsHttpOnlyCookie() throws Exception {
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
			.andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.allOf(
				org.hamcrest.Matchers.containsString("ps_guest_id="),
				org.hamcrest.Matchers.containsString("HttpOnly"),
				org.hamcrest.Matchers.containsString("SameSite=Lax"),
				org.hamcrest.Matchers.containsString("Max-Age=604800")
			)))
			.andExpect(header().string(
				"X-Guest-Submissions-Remaining",
				String.valueOf(GuestSessionService.SUBMISSION_LIMIT - 1)
			));

		String guestSessionId = jdbcTemplate.queryForObject(
			"SELECT guest_session_id FROM submissions WHERE problem_id = ?",
			String.class,
			problem.getId()
		);
		org.assertj.core.api.Assertions.assertThat(guestSessionId).isNotBlank();
	}

	@Test
	void createGuestSubmissionAllowsMoreThanFiveSubmissions() throws Exception {
		Problem problem = createProblem();
		GuestSubmissionQuota quota = guestSessionService.consumeSubmission(null);
		for (int count = 1; count < 5; count++) {
			guestSessionService.consumeSubmission(quota.guestSessionId());
		}

		mockMvc.perform(post("/api/problems/{problemId}/submissions", problem.getId())
				.cookie(new Cookie("ps_guest_id", quota.guestSessionId()))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "language": "java",
					  "sourceCode": "public class Main {}"
					}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(header().string(
				"X-Guest-Submissions-Remaining",
				String.valueOf(GuestSessionService.SUBMISSION_LIMIT - 6)
			));
	}

	@Test
	void createSubmissionRejectsTooManyRequestsForSameUser() throws Exception {
		Problem problem = createProblem();
		String authorization = authorizationFor("alice@example.com", "alice");

		String body = """
			{
			  "language": "java",
			  "sourceCode": "public class Main {}"
			}
			""";

		mockMvc.perform(post("/api/problems/{problemId}/submissions", problem.getId())
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isAccepted());

		mockMvc.perform(post("/api/problems/{problemId}/submissions", problem.getId())
				.header("Authorization", authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().string("Retry-After", "1"))
			.andExpect(jsonPath("$.message").value("요청이 너무 많습니다. 잠시 후 다시 시도해주세요."))
			.andExpect(jsonPath("$.retryAfterSeconds").value(1));
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
	void findSubmissionsByProblemIdExcludesAdminSubmission() throws Exception {
		Problem problem = createProblem();
		User user = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");
		User admin = userRepository.save("admin@example.com", "admin", "hashed-password", "ADMIN");
		Submission userSubmission = submissionRepository.save(
			problem.getId(),
			user.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		Submission adminSubmission = submissionRepository.save(
			problem.getId(),
			admin.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);

		mockMvc.perform(get("/api/problems/{problemId}/submissions", problem.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(userSubmission.getId()))
			.andExpect(jsonPath("$[1]").doesNotExist());

		org.assertj.core.api.Assertions.assertThat(submissionRepository.findById(adminSubmission.getId()))
			.isPresent();
	}

	@Test
	void findSubmissionsKeepsWithdrawnUserSubmission() throws Exception {
		Problem problem = createProblem();
		User user = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");
		Submission submission = submissionRepository.save(
			problem.getId(),
			user.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);

		userRepository.deleteById(user.getId());

		mockMvc.perform(get("/api/problems/{problemId}/submissions", problem.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(submission.getId()))
			.andExpect(jsonPath("$[0].userId").value(user.getId()))
			.andExpect(jsonPath("$[0].username").value("탈퇴한 사용자"));
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
	void findMySolvedProblemIdsReturnsAcceptedProblemIdsForCurrentUser() throws Exception {
		Problem acceptedProblem = createProblem("A+B");
		Problem wrongProblem = createProblem("A-B");
		Problem otherUserProblem = createProblem("A*C");
		User alice = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");
		User bob = userRepository.save("bob@example.com", "bob", "hashed-password", "USER");
		String authorization = "Bearer " + jwtTokenService.createAccessToken(alice);

		Submission acceptedSubmission = submissionRepository.save(
			acceptedProblem.getId(),
			alice.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		Submission wrongSubmission = submissionRepository.save(
			wrongProblem.getId(),
			alice.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		Submission otherUserSubmission = submissionRepository.save(
			otherUserProblem.getId(),
			bob.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);

		markJudged(acceptedSubmission.getId(), SubmissionVerdict.ACCEPTED);
		markJudged(wrongSubmission.getId(), SubmissionVerdict.WRONG_ANSWER);
		markJudged(otherUserSubmission.getId(), SubmissionVerdict.ACCEPTED);

		mockMvc.perform(get("/api/me/solved-problems")
				.header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0]").value(acceptedProblem.getId()))
			.andExpect(jsonPath("$[1]").doesNotExist());
	}

	@Test
	void solvedSummaryCountsDistinctAcceptedProblemsByDifficultyAndCategory() throws Exception {
		Problem easy = createProblem("쉬운 합", ProblemDifficulty.EASY, List.of("수학"));
		Problem hard = createProblem("어려운 점화식", ProblemDifficulty.HARD, List.of("수학", "동적 계획법"));
		Problem wrong = createProblem("틀린 문제", ProblemDifficulty.NORMAL, List.of("구현"));
		User alice = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");
		String authorization = "Bearer " + jwtTokenService.createAccessToken(alice);

		markAccepted(easy.getId(), alice.getId());
		markAccepted(easy.getId(), alice.getId());
		markAccepted(hard.getId(), alice.getId());
		Submission wrongSubmission = submissionRepository.save(
			wrong.getId(),
			alice.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		markJudged(wrongSubmission.getId(), SubmissionVerdict.WRONG_ANSWER);

		mockMvc.perform(get("/api/me/solved-summary").header("Authorization", authorization))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalSolved").value(2))
			.andExpect(jsonPath("$.difficulties[0].difficulty").value("EASY"))
			.andExpect(jsonPath("$.difficulties[0].count").value(1))
			.andExpect(jsonPath("$.difficulties[1].difficulty").value("NORMAL"))
			.andExpect(jsonPath("$.difficulties[1].count").value(0))
			.andExpect(jsonPath("$.difficulties[2].difficulty").value("HARD"))
			.andExpect(jsonPath("$.difficulties[2].count").value(1))
			.andExpect(jsonPath("$.categories[?(@.category == '수학')].count").value(
				org.hamcrest.Matchers.hasItem(2)
			))
			.andExpect(jsonPath("$.categories[?(@.category == '동적 계획법')].count").value(
				org.hamcrest.Matchers.hasItem(1)
			));
	}

	@Test
	void solvedProblemDetailsApplySearchFiltersSortAndPagination() throws Exception {
		Problem easy = createProblem("기초 점화식", ProblemDifficulty.EASY, List.of("동적 계획법"));
		Problem hard = createProblem("최장 증가 부분 수열", ProblemDifficulty.HARD, List.of("동적 계획법"));
		Problem graph = createProblem("최장 경로", ProblemDifficulty.HARD, List.of("그래프"));
		Problem archived = createProblem("보관된 최장 문제", ProblemDifficulty.HARD, List.of("동적 계획법"));
		User alice = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");
		User bob = userRepository.save("bob@example.com", "bob", "hashed-password", "USER");
		String authorization = "Bearer " + jwtTokenService.createAccessToken(alice);

		markAccepted(easy.getId(), alice.getId());
		markAccepted(hard.getId(), alice.getId());
		markAccepted(hard.getId(), alice.getId());
		markAccepted(graph.getId(), alice.getId());
		markAccepted(archived.getId(), alice.getId());
		markAccepted(createProblem("다른 사용자 문제").getId(), bob.getId());
		jdbcTemplate.update("UPDATE problems SET archived_at = CURRENT_TIMESTAMP WHERE id = ?", archived.getId());

		mockMvc.perform(get("/api/me/solved-problem-details")
				.header("Authorization", authorization)
				.param("query", "최장")
				.param("difficulty", "HARD")
				.param("category", "동적 계획법")
				.param("sort", "PROBLEM_ID_ASC")
				.param("page", "0")
				.param("size", "1"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.totalPages").value(1))
			.andExpect(jsonPath("$.problems[0].problemId").value(hard.getId()))
			.andExpect(jsonPath("$.problems[0].title").value("최장 증가 부분 수열"))
			.andExpect(jsonPath("$.problems[0].difficulty").value("HARD"))
			.andExpect(jsonPath("$.problems[0].categories[0]").value("동적 계획법"))
			.andExpect(jsonPath("$.problems[0].solvedAt", org.hamcrest.Matchers.endsWith("Z")));
	}

	@Test
	void administratorSubmissionsAreExcludedFromSolvedSummary() throws Exception {
		Problem problem = createProblem("관리자 정답");
		User admin = userRepository.save("admin@example.com", "admin", "hashed-password", "ADMIN");
		markAccepted(problem.getId(), admin.getId());

		mockMvc.perform(get("/api/me/solved-summary")
				.header("Authorization", "Bearer " + jwtTokenService.createAccessToken(admin)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalSolved").value(0));
	}

	@Test
	void todayStatsReturnsTodaySubmissionCount() throws Exception {
		Problem problem = createProblem();
		User alice = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");
		User bob = userRepository.save("bob@example.com", "bob", "hashed-password", "USER");
		User admin = userRepository.save("admin@example.com", "admin", "hashed-password", "ADMIN");
		submissionRepository.save(
			problem.getId(),
			alice.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		submissionRepository.save(
			problem.getId(),
			alice.getId(),
			new SubmissionCreateRequest("python", "print(1)")
		);
		submissionRepository.save(
			problem.getId(),
			bob.getId(),
			new SubmissionCreateRequest("cpp", "int main() { return 0; }")
		);
		submissionRepository.save(
			problem.getId(),
			admin.getId(),
			new SubmissionCreateRequest("java", "public class Main {}")
		);

		mockMvc.perform(get("/api/submissions/stats/today"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.todaySubmissionCount").value(3))
			.andExpect(jsonPath("$.todaySolverCount").value(2));
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
		return createProblem("A+B");
	}

	private Problem createProblem(String title) {
		return createProblem(title, ProblemDifficulty.NORMAL, List.of());
	}

	private Problem createProblem(String title, ProblemDifficulty difficulty, List<String> categories) {
		Problem problem = problemRepository.save(new ProblemCreateRequest(
			title,
			"문제 설명",
			"입력 설명",
			"출력 설명",
			1000,
			128,
			difficulty,
			categories,
			List.of()
		));
		problemCategoryRepository.addLinks(problem.getId(), categories);
		return problem;
	}

	private String authorizationFor(String email, String username) {
		User user = userRepository.save(email, username, "hashed-password", "USER");
		return "Bearer " + jwtTokenService.createAccessToken(user);
	}

	private void markJudged(long submissionId, SubmissionVerdict verdict) {
		jdbcTemplate.update(
			"UPDATE submissions SET status = ?, verdict = ? WHERE id = ?",
			SubmissionStatus.JUDGED.name(),
			verdict.name(),
			submissionId
		);
	}

	private void markAccepted(long problemId, long userId) {
		Submission submission = submissionRepository.save(
			problemId,
			userId,
			new SubmissionCreateRequest("java", "public class Main {}")
		);
		markJudged(submission.getId(), SubmissionVerdict.ACCEPTED);
	}
}
