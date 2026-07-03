package com.psplatform.backend.support;

public class SupportInquiryDeliveryException extends RuntimeException {

	public SupportInquiryDeliveryException(Throwable cause) {
		super("문의 메일 전송에 실패했습니다.", cause);
	}
}
