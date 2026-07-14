package com.psplatform.backend.emailverification;

public class EmailVerificationRequestLimitException extends RuntimeException {

	public EmailVerificationRequestLimitException() {
		super("인증번호는 1분 후에 다시 요청할 수 있습니다.");
	}
}
