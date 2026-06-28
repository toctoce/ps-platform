package com.psplatform.backend.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
			.andReturn()
			.getResponse()
			.getContentAsString();

		String accessToken = JsonPath.read(response, "$.accessToken");
		mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(EMAIL))
			.andExpect(jsonPath("$.username").value(USERNAME));
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
}
