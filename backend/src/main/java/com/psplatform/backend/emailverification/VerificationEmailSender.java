package com.psplatform.backend.emailverification;

public interface VerificationEmailSender {

	void sendSignupCode(String email, String code, long expiresInSeconds);
}
