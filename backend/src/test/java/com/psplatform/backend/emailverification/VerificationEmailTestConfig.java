package com.psplatform.backend.emailverification;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class VerificationEmailTestConfig {

	@Bean
	@Primary
	RecordingVerificationEmailSender recordingVerificationEmailSender() {
		return new RecordingVerificationEmailSender();
	}
}
