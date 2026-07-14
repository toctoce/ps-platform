package com.psplatform.backend.user;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.emailverification.EmailVerificationRepository;
import com.psplatform.backend.emailverification.EmailVerificationService;
import com.psplatform.backend.emailverification.RecordingVerificationEmailSender;
import com.psplatform.backend.emailverification.VerificationEmailTestConfig;

@SpringBootTest
@AutoConfigureMockMvc
@Import(VerificationEmailTestConfig.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EmailVerificationRepository verificationRepository;

	@Autowired
	private EmailVerificationService emailVerificationService;

	@Autowired
	private RecordingVerificationEmailSender emailSender;

	@BeforeEach
	void setUp() {
		verificationRepository.deleteAll();
		userRepository.deleteAll();
		emailSender.clear();
	}

	@Test
	void signupUser() throws Exception {
		String verificationToken = issueVerificationToken("alice@example.com");

		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "alice@example.com",
					  "username": "alice",
					  "password": "password1234",
					  "verificationToken": "%s"
					}
					""".formatted(verificationToken)))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", org.hamcrest.Matchers.matchesPattern("/api/users/[0-9]+")))
			.andExpect(jsonPath("$.id").isNumber())
			.andExpect(jsonPath("$.email").value("alice@example.com"))
			.andExpect(jsonPath("$.username").value("alice"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void findUserById() throws Exception {
		User user = userRepository.save("bob@example.com", "bob", "hashed-password", "USER");

		mockMvc.perform(get("/api/users/{id}", user.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId()))
			.andExpect(jsonPath("$.email").value("bob@example.com"))
			.andExpect(jsonPath("$.username").value("bob"))
			.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void signupRejectsDuplicateEmail() throws Exception {
		userRepository.save("dupe@example.com", "first", "hashed-password", "USER");

		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "dupe@example.com",
					  "username": "second",
					  "password": "password1234"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Email already exists: dupe@example.com"));
	}

	@Test
	void signupNormalizesEmailAndRejectsCaseInsensitiveDuplicate() throws Exception {
		String verificationToken = issueVerificationToken("Dupe@Example.com");

		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "Dupe@Example.com",
					  "username": "first",
					  "password": "password1234",
					  "verificationToken": "%s"
					}
					""".formatted(verificationToken)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("dupe@example.com"));

		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "dupe@example.com",
					  "username": "second",
					  "password": "password1234"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Email already exists: dupe@example.com"));
	}

	@Test
	void signupRejectsDuplicateUsername() throws Exception {
		userRepository.save("first@example.com", "same-name", "hashed-password", "USER");

		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "second@example.com",
					  "username": "same-name",
					  "password": "password1234"
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Username already exists: same-name"));
	}

	@Test
	void signupRejectsInvalidEmail() throws Exception {
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "bad-email",
					  "username": "invalid-email",
					  "password": "password1234"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Invalid email format"));
	}

	@Test
	void signupRejectsShortPassword() throws Exception {
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "shortpw@example.com",
					  "username": "shortpw",
					  "password": "1234"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("Password must be at least 8 characters"));
	}

	@Test
	void signupRejectsMissingEmailVerification() throws Exception {
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "not-verified@example.com",
					  "username": "not-verified",
					  "password": "password1234"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이메일 인증이 필요합니다."));
	}

	@Test
	void signupRejectsVerificationTokenIssuedForAnotherEmail() throws Exception {
		String verificationToken = issueVerificationToken("verified@example.com");

		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "another@example.com",
					  "username": "another",
					  "password": "password1234",
					  "verificationToken": "%s"
					}
					""".formatted(verificationToken)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("이메일 인증이 만료되었거나 유효하지 않습니다."));
	}

	@Test
	void findUserByIdNotFound() throws Exception {
		mockMvc.perform(get("/api/users/{id}", 9999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("User not found: 9999"));
	}

	private String issueVerificationToken(String email) {
		emailVerificationService.requestSignupCode(email);
		String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
		String code = emailSender.codeFor(normalizedEmail);
		return emailVerificationService.confirmSignupCode(email, code).value();
	}
}
