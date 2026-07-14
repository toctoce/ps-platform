package com.psplatform.backend.emailverification;

import java.util.HashMap;
import java.util.Map;

public class RecordingVerificationEmailSender implements VerificationEmailSender {

	private final Map<String, String> codesByEmail = new HashMap<>();

	@Override
	public void sendSignupCode(String email, String code, long expiresInSeconds) {
		codesByEmail.put(email, code);
	}

	public String codeFor(String email) {
		return codesByEmail.get(email);
	}

	public void clear() {
		codesByEmail.clear();
	}
}
