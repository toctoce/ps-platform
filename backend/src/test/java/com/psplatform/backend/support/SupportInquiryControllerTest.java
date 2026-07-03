package com.psplatform.backend.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.psplatform.backend.auth.JwtTokenService;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SupportInquiryControllerTest.NotifierTestConfig.class)
class SupportInquiryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RecordingSupportInquiryNotifier notifier;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenService jwtTokenService;

	@BeforeEach
	void setUp() {
		notifier.clear();
		userRepository.deleteAll();
	}

	@Test
	void createSupportInquiryWithoutLogin() throws Exception {
		mockMvc.perform(post("/api/support/inquiries")
				.with(request -> {
					request.setRemoteAddr("203.0.113.10");
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "guest@example.com",
					  "type": "BUG",
					  "subject": "화면 오류",
					  "message": "홈 화면에서 버튼이 눌리지 않습니다."
					}
					"""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.message").value("문의가 접수되었습니다."));

		assertThat(notifier.inquiries()).hasSize(1);
		SupportInquiry inquiry = notifier.inquiries().getFirst();
		assertThat(inquiry.email()).isEqualTo("guest@example.com");
		assertThat(inquiry.type()).isEqualTo(SupportInquiryType.BUG);
		assertThat(inquiry.userId()).isNull();
	}

	@Test
	void createSupportInquiryUsesLoggedInUserEmailWhenEmailIsBlank() throws Exception {
		User user = userRepository.save("alice@example.com", "alice", "hashed-password", "USER");

		mockMvc.perform(post("/api/support/inquiries")
				.with(request -> {
					request.setRemoteAddr("203.0.113.11");
					return request;
				})
				.header("Authorization", "Bearer " + jwtTokenService.createAccessToken(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "",
					  "type": "ACCOUNT",
					  "subject": "계정 문의",
					  "message": "닉네임 변경이 필요합니다."
					}
					"""))
			.andExpect(status().isAccepted());

		SupportInquiry inquiry = notifier.inquiries().getFirst();
		assertThat(inquiry.email()).isEqualTo("alice@example.com");
		assertThat(inquiry.userId()).isEqualTo(user.getId());
		assertThat(inquiry.username()).isEqualTo("alice");
	}

	@Test
	void createSupportInquiryRejectsInvalidEmail() throws Exception {
		mockMvc.perform(post("/api/support/inquiries")
				.with(request -> {
					request.setRemoteAddr("203.0.113.12");
					return request;
				})
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "email": "bad-email",
					  "type": "OTHER",
					  "subject": "문의",
					  "message": "내용입니다."
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("올바른 이메일을 입력해 주세요."));

		assertThat(notifier.inquiries()).isEmpty();
	}

	@TestConfiguration
	static class NotifierTestConfig {

		@Bean
		@Primary
		RecordingSupportInquiryNotifier recordingSupportInquiryNotifier() {
			return new RecordingSupportInquiryNotifier();
		}
	}

	static class RecordingSupportInquiryNotifier implements SupportInquiryNotifier {

		private final List<SupportInquiry> inquiries = new ArrayList<>();

		@Override
		public void notify(SupportInquiry inquiry) {
			inquiries.add(inquiry);
		}

		List<SupportInquiry> inquiries() {
			return inquiries;
		}

		void clear() {
			inquiries.clear();
		}
	}
}
