package com.psplatform.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

	private static final String EMAIL = "alice@example.com";
	private static final String USERNAME = "alice";
	private static final String PASSWORD = "password1234";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
		userRepository.save(
			EMAIL,
			USERNAME,
			passwordEncoder.encode(PASSWORD),
			"USER"
		);
	}

	@Test
	void loginReturnsTokenPair() throws Exception {
		String response = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "password1234"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.refreshToken").isString())
			.andExpect(jsonPath("$.expiresIn").value(900))
			.andExpect(jsonPath("$.user.email").value(EMAIL))
			.andExpect(jsonPath("$.user.level").value(1))
			.andExpect(jsonPath("$.user.totalExperience").value(0))
			.andExpect(jsonPath("$.user.requiredExperience").value(50))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String accessToken = JsonPath.read(response, "$.accessToken");
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.username").value(USERNAME))
			.andExpect(jsonPath("$.level").value(1))
			.andExpect(jsonPath("$.progressPercent").value(0));
	}

	@Test
	void loginNormalizesEmailCase() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "Alice@Example.com",
					    "password": "password1234"
					  }
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.email").value(EMAIL));
	}

	@Test
	void loginFailsWhenCredentialInvalid() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "wrong"
					  }
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void refreshIssuesNewAccessTokenAndRefreshToken() throws Exception {
		String loginResponse = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "password1234"
					  }
					}
					"""))
			.andReturn()
			.getResponse()
			.getContentAsString();

		String oldRefreshToken = JsonPath.read(loginResponse, "$.refreshToken");

		String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
				{
					  "refreshToken": "%s"
					}
					""".formatted(oldRefreshToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.accessToken").isString())
			.andExpect(jsonPath("$.refreshToken").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String newAccessToken = JsonPath.read(refreshResponse, "$.accessToken");
		String newRefreshToken = JsonPath.read(refreshResponse, "$.refreshToken");
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + newAccessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL));
		org.assertj.core.api.Assertions.assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
	}

	@Test
	void refreshFailsWithInvalidToken() throws Exception {
		mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "invalid"
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));
	}

	@Test
	void logoutRevokesRefreshToken() throws Exception {
		String loginResponse = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "password1234"
					  }
					}
					"""))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String refreshToken = JsonPath.read(loginResponse, "$.refreshToken");

		mockMvc.perform(post("/api/auth/logout")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(refreshToken)))
			.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(refreshToken)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));
	}

	@Test
	void deleteMeSoftDeletesCurrentUserAndRefreshTokens() throws Exception {
		String loginResponse = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "password1234"
					  }
					}
					"""))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String accessToken = JsonPath.read(loginResponse, "$.accessToken");
		String refreshToken = JsonPath.read(loginResponse, "$.refreshToken");
		Number userId = JsonPath.read(loginResponse, "$.user.id");

		mockMvc.perform(delete("/api/auth/me").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isNoContent());

		Assertions.assertThat(userRepository.findByEmail(EMAIL)).isEmpty();
		Assertions.assertThat(userRepository.findById(userId.longValue()))
			.hasValueSatisfying(user -> {
				Assertions.assertThat(user.isDeleted()).isTrue();
				Assertions.assertThat(user.getEmail()).isEqualTo("deleted-user-%d@deleted.local".formatted(userId.longValue()));
				Assertions.assertThat(user.getUsername()).isEqualTo("deleted-user-%d".formatted(userId.longValue()));
				Assertions.assertThat(user.getPasswordHash()).isEqualTo("deleted-password");
				Assertions.assertThat(user.getRole()).isEqualTo("WITHDRAWN");
			});
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("User not found"));
		mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "refreshToken": "%s"
					}
					""".formatted(refreshToken)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid refresh token"));
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "password1234"
					  }
					}
					"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Invalid email or password"));
	}

	@Test
	void deleteMeRequiresAccessToken() throws Exception {
		mockMvc.perform(delete("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Authorization header is required"));
	}

	@Test
	void updateUsernameChangesCurrentUser() throws Exception {
		String accessToken = loginAndRead("$.accessToken");

		mockMvc.perform(patch("/api/auth/me/username")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"username": "next-alice"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.username").value("next-alice"))
			.andExpect(jsonPath("$.email").value(EMAIL));

		Assertions.assertThat(userRepository.findByEmail(EMAIL))
			.hasValueSatisfying(user -> Assertions.assertThat(user.getUsername()).isEqualTo("next-alice"));
	}

	@Test
	void updateUsernameRejectsDuplicateUsername() throws Exception {
		userRepository.save("bob@example.com", "bob", passwordEncoder.encode(PASSWORD), "USER");
		String accessToken = loginAndRead("$.accessToken");

		mockMvc.perform(patch("/api/auth/me/username")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"username": "bob"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Username already exists: bob"));
	}

	@Test
	void changePasswordRevokesCurrentTokensAndAcceptsNewPassword() throws Exception {
		String loginResponse = login();
		String accessToken = JsonPath.read(loginResponse, "$.accessToken");
		String refreshToken = JsonPath.read(loginResponse, "$.refreshToken");

		mockMvc.perform(patch("/api/auth/me/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "currentPassword": "password1234",
					  "newPassword": "changed-password"
					}
					"""))
			.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/refresh")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"refreshToken\": \"%s\"}".formatted(refreshToken)))
			.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "changed-password"
					  }
					}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void changePasswordRejectsIncorrectCurrentPassword() throws Exception {
		String accessToken = loginAndRead("$.accessToken");

		mockMvc.perform(patch("/api/auth/me/password")
				.header("Authorization", "Bearer " + accessToken)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "currentPassword": "incorrect",
					  "newPassword": "changed-password"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("현재 비밀번호가 올바르지 않습니다."));
	}

	private String loginAndRead(String path) throws Exception {
		return JsonPath.read(login(), path);
	}

	private String login() throws Exception {
		return mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "provider": "local",
					  "credentials": {
					    "email": "alice@example.com",
					    "password": "password1234"
					  }
					}
					"""))
			.andReturn()
			.getResponse()
			.getContentAsString();
	}
}
