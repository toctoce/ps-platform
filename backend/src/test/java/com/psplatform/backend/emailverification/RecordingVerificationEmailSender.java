package com.psplatform.backend.emailverification;

import java.util.HashMap;
import java.util.Map;

public class RecordingVerificationEmailSender implements VerificationEmailSender {

	private final Map<String, String> codesByEmail = new HashMap<>();

	@Override
	public void sendCode(
		String email,
		String code,
		EmailVerificationPurpose purpose,
		long expiresInSeconds
	) {
		codesByEmail.put(key(email, purpose), code);
	}

	public String codeFor(String email) {
		return codeFor(email, EmailVerificationPurpose.SIGNUP);
	}

	public String codeFor(String email, EmailVerificationPurpose purpose) {
		return codesByEmail.get(key(email, purpose));
	}

	public void clear() {
		codesByEmail.clear();
	}

	private String key(String email, EmailVerificationPurpose purpose) {
		return purpose.name() + ":" + email;
	}
}
