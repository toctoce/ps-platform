package com.psplatform.backend.emailverification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(VerificationEmailTestConfig.class)
class EmailVerificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private EmailVerificationRepository verificationRepository;

	@Autowired
	private EmailVerificationService emailVerificationService;

	@Autowired
	private RecordingVerificationEmailSender emailSender;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		verificationRepository.deleteAll();
		userRepository.deleteAll();
		emailSender.clear();
	}

	@Test
	void requestAndConfirmSignupCode() throws Exception {
		mockMvc.perform(post("/api/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"Alice@Example.com"}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.message").value("인증번호를 전송했습니다."))
			.andExpect(jsonPath("$.expiresInSeconds").value(300));

		String code = emailSender.codeFor("alice@example.com");
		assertThat(code).matches("\\d{6}");

		mockMvc.perform(post("/api/email-verifications/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"alice@example.com","code":"%s"}
					""".formatted(code)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verificationToken").isString())
			.andExpect(jsonPath("$.expiresInSeconds").value(1800));
	}

	@Test
	void rejectWrongCodeAndCountAttempt() throws Exception {
		requestCode("wrong-code@example.com");
		String issuedCode = emailSender.codeFor("wrong-code@example.com");
		String wrongCode = (issuedCode.startsWith("0") ? "1" : "0") + issuedCode.substring(1);

		mockMvc.perform(post("/api/email-verifications/confirm")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"wrong-code@example.com","code":"%s"}
					""".formatted(wrongCode)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("인증번호가 올바르지 않습니다."));

		EmailVerification verification = verificationRepository
			.findByEmailAndPurpose("wrong-code@example.com", EmailVerificationPurpose.SIGNUP)
			.orElseThrow();
		assertThat(verification.attemptCount()).isEqualTo(1);
	}

	@Test
	void rejectCodeResendDuringCooldown() throws Exception {
		requestCode("cooldown@example.com");

		mockMvc.perform(post("/api/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"cooldown@example.com"}
					"""))
			.andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.message").value("인증번호는 1분 후에 다시 요청할 수 있습니다."));
	}

	@Test
	void rejectVerificationForExistingUserEmail() throws Exception {
		userRepository.save("member@example.com", "member", "password-hash", "USER");

		mockMvc.perform(post("/api/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"member@example.com"}
					"""))
			.andExpect(status().isConflict());
	}

	@Test
	void signupTokenCanBeConsumedOnlyOnce() {
		emailVerificationService.requestSignupCode("single-use@example.com");
		String code = emailSender.codeFor("single-use@example.com");
		String token = emailVerificationService.confirmSignupCode("single-use@example.com", code).value();

		emailVerificationService.consumeSignupToken("single-use@example.com", token);

		assertThatThrownBy(() -> emailVerificationService.consumeSignupToken("single-use@example.com", token))
			.isInstanceOf(InvalidEmailVerificationException.class)
			.hasMessage("이메일 인증이 만료되었거나 유효하지 않습니다.");
	}

	private void requestCode(String email) throws Exception {
		mockMvc.perform(post("/api/email-verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s"}
					""".formatted(email)))
			.andExpect(status().isAccepted());
	}
}
