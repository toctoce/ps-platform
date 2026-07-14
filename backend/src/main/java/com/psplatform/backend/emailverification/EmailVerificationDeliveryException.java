package com.psplatform.backend.emailverification;

public class EmailVerificationDeliveryException extends RuntimeException {

	public EmailVerificationDeliveryException(Throwable cause) {
		super("인증 메일을 전송하지 못했습니다. 잠시 후 다시 시도해 주세요.", cause);
	}
}
