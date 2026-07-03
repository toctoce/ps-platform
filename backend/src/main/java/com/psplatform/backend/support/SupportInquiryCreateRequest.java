package com.psplatform.backend.support;

public record SupportInquiryCreateRequest(
	String email,
	String subject,
	String message,
	SupportInquiryType type
) {
}
