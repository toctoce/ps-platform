package com.psplatform.backend.emailverification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationConfig {

	private final String codeHashSecret;
	private final long codeTtlSeconds;
	private final long tokenTtlSeconds;
	private final long resendCooldownSeconds;
	private final int maxAttempts;

	public EmailVerificationConfig(
		@Value("${app.email-verification.code-hash-secret}") String codeHashSecret,
		@Value("${app.email-verification.code-ttl-seconds:300}") long codeTtlSeconds,
		@Value("${app.email-verification.token-ttl-seconds:1800}") long tokenTtlSeconds,
		@Value("${app.email-verification.resend-cooldown-seconds:60}") long resendCooldownSeconds,
		@Value("${app.email-verification.max-attempts:5}") int maxAttempts
	) {
		if (codeHashSecret == null || codeHashSecret.isBlank()) {
			throw new IllegalArgumentException("Email verification code hash secret is required");
		}
		this.codeHashSecret = codeHashSecret;
		this.codeTtlSeconds = codeTtlSeconds;
		this.tokenTtlSeconds = tokenTtlSeconds;
		this.resendCooldownSeconds = resendCooldownSeconds;
		this.maxAttempts = maxAttempts;
	}

	public String getCodeHashSecret() {
		return codeHashSecret;
	}

	public long getCodeTtlSeconds() {
		return codeTtlSeconds;
	}

	public long getTokenTtlSeconds() {
		return tokenTtlSeconds;
	}

	public long getResendCooldownSeconds() {
		return resendCooldownSeconds;
	}

	public int getMaxAttempts() {
		return maxAttempts;
	}
}
