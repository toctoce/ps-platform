package com.psplatform.backend.support;

import java.time.LocalDateTime;

public record SupportInquiry(
	String email,
	String subject,
	String message,
	SupportInquiryType type,
	Long userId,
	String username,
	String remoteAddress,
	LocalDateTime createdAt
) {
}
