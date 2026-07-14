package com.psplatform.backend.emailverification;

public interface VerificationEmailSender {

	void sendCode(
		String email,
		String code,
		EmailVerificationPurpose purpose,
		long expiresInSeconds
	);
}
