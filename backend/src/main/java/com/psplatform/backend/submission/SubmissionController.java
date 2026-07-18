package com.psplatform.backend.submission;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.auth.AuthService;
import com.psplatform.backend.problem.ProblemDifficulty;
import com.psplatform.backend.user.User;

@RestController
public class SubmissionController {
	private static final String GUEST_COOKIE_NAME = "ps_guest_id";

	private final SubmissionService submissionService;
	private final AuthService authService;

	public SubmissionController(SubmissionService submissionService, AuthService authService) {
		this.submissionService = submissionService;
		this.authService = authService;
	}

	@PostMapping("/api/problems/{problemId}/submissions")
	public ResponseEntity<SubmissionResponse> create(
		@PathVariable long problemId,
		@RequestBody SubmissionCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@CookieValue(value = GUEST_COOKIE_NAME, required = false) String guestSessionId,
		HttpServletRequest servletRequest
	) {
		Optional<User> user = authService.findUserIfAuthenticated(authorization);
		SubmissionCreationResult result = submissionService.createForRequester(
			problemId,
			request,
			user.map(User::getId).orElse(null),
			guestSessionId
		);
		ResponseEntity.BodyBuilder response = ResponseEntity
			.accepted()
			.location(URI.create("/api/submissions/" + result.submission().getId()));
		if (result.guestQuota() != null) {
			response.header("X-Guest-Submissions-Remaining", String.valueOf(result.guestQuota().remaining()));
			if (result.guestQuota().newSession()) {
				response.header(HttpHeaders.SET_COOKIE, guestCookie(result.guestQuota().guestSessionId(), servletRequest));
			}
		}
		return response.body(SubmissionResponse.from(result.submission()));
	}

	private String guestCookie(String guestSessionId, HttpServletRequest request) {
		boolean secure = request.isSecure()
			|| "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
		return ResponseCookie.from(GUEST_COOKIE_NAME, guestSessionId)
			.httpOnly(true)
			.secure(secure)
			.sameSite("Lax")
			.path("/")
			.maxAge(Duration.ofDays(7))
			.build()
			.toString();
	}

	@GetMapping("/api/problems/{problemId}/submissions")
	public List<SubmissionResponse> findByProblemId(@PathVariable long problemId) {
		return submissionService.findByProblemId(problemId)
			.stream()
			.map(SubmissionResponse::from)
			.toList();
	}

	@GetMapping("/api/submissions/{id}")
	public SubmissionResponse findById(@PathVariable long id) {
		return SubmissionResponse.from(submissionService.findById(id));
	}

	@GetMapping("/api/me/solved-problems")
	public List<Long> findMySolvedProblemIds(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User user = authService.verifyAccessToken(authorization);
		return submissionService.findAcceptedProblemIdsByUserId(user.getId());
	}

	@GetMapping("/api/me/solved-summary")
	public SolvedProblemSummaryResponse findMySolvedProblemSummary(
		@RequestHeader(value = "Authorization", required = false) String authorization
	) {
		User user = authService.verifyAccessToken(authorization);
		return submissionService.findSolvedProblemSummary(user.getId());
	}

	@GetMapping("/api/me/solved-problem-details")
	public SolvedProblemListResponse findMySolvedProblems(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String query,
		@org.springframework.web.bind.annotation.RequestParam(required = false) ProblemDifficulty difficulty,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String category,
		@org.springframework.web.bind.annotation.RequestParam(
			defaultValue = "SOLVED_AT_DESC"
		) SolvedProblemSearch.Sort sort,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
		@org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int size
	) {
		User user = authService.verifyAccessToken(authorization);
		SolvedProblemSearch search = new SolvedProblemSearch(
			query,
			difficulty,
			category,
			sort,
			Math.max(0, page),
			Math.max(1, Math.min(size, 100))
		);
		return submissionService.findSolvedProblems(user.getId(), search);
	}

	@GetMapping("/api/submissions/stats/today")
	public SubmissionStatsResponse todayStats() {
		return submissionService.todayStats();
	}
}
