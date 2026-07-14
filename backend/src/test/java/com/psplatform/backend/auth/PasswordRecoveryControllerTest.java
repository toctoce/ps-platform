package com.psplatform.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.emailverification.EmailVerificationPurpose;
import com.psplatform.backend.emailverification.EmailVerificationRepository;
import com.psplatform.backend.emailverification.EmailVerificationService;
import com.psplatform.backend.emailverification.RecordingVerificationEmailSender;
import com.psplatform.backend.emailverification.VerificationEmailTestConfig;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(VerificationEmailTestConfig.class)
class PasswordRecoveryControllerTest {

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

	@Autowired
	private RefreshTokenService refreshTokenService;

	@Autowired
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		verificationRepository.deleteAll();
		userRepository.deleteAll();
		emailSender.clear();
	}

	@Test
	void resetPasswordAndRevokeRefreshTokens() throws Exception {
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		User user = userRepository.save(
			"member@example.com",
			"member",
			encoder.encode("old-password"),
			"USER"
		);
		String oldRefreshToken = refreshTokenService.createForUser(user).refreshToken();
		String oldAccessToken = jwtTokenService.createAccessToken(user);

		requestVerification("member@example.com");
		String code = emailSender.codeFor("member@example.com", EmailVerificationPurpose.PASSWORD_RESET);
		String verificationToken = emailVerificationService
			.confirmPasswordResetCode("member@example.com", code)
			.value();

		mockMvc.perform(post("/api/password-recovery/reset")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email":"member@example.com",
					  "verificationToken":"%s",
					  "newPassword":"new-password"
					}
					""".formatted(verificationToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.message").value("비밀번호가 변경되었습니다."));

		User updatedUser = userRepository.findById(user.getId()).orElseThrow();
		assertThat(encoder.matches("new-password", updatedUser.getPasswordHash())).isTrue();
		assertThat(encoder.matches("old-password", updatedUser.getPasswordHash())).isFalse();
		assertThatThrownBy(() -> refreshTokenService.assertValidAndGet(oldRefreshToken))
			.isInstanceOf(RefreshTokenInvalidException.class);
		mockMvc.perform(get("/api/auth/me")
				.header("Authorization", "Bearer " + oldAccessToken))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void returnSameResponseForUnknownEmailWithoutSendingMail() throws Exception {
		requestVerification("unknown@example.com");

		assertThat(emailSender.codeFor("unknown@example.com", EmailVerificationPurpose.PASSWORD_RESET)).isNull();
	}

	@Test
	void rejectPasswordResetWithoutVerification() throws Exception {
		userRepository.save("member@example.com", "member", "password-hash", "USER");

		mockMvc.perform(post("/api/password-recovery/reset")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email":"member@example.com",
					  "verificationToken":"invalid-token",
					  "newPassword":"new-password"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message")
				.value("이메일 인증이 만료되었거나 유효하지 않습니다."));
	}

	private void requestVerification(String email) throws Exception {
		mockMvc.perform(post("/api/password-recovery/verifications")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s"}
					""".formatted(email)))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.message").value("입력한 이메일로 인증번호를 전송했습니다."));
	}
}
