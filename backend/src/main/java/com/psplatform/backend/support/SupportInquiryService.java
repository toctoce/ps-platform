package com.psplatform.backend.support;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.psplatform.backend.ratelimit.RateLimiter;
import com.psplatform.backend.user.User;

@Service
public class SupportInquiryService {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final int SUBJECT_MAX_LENGTH = 120;
	private static final int MESSAGE_MAX_LENGTH = 4000;
	private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

	private final SupportInquiryNotifier notifier;
	private final RateLimiter rateLimiter;

	public SupportInquiryService(SupportInquiryNotifier notifier, RateLimiter rateLimiter) {
		this.notifier = notifier;
		this.rateLimiter = rateLimiter;
	}

	public SupportInquiry create(SupportInquiryCreateRequest request, User user, String remoteAddress) {
		SupportInquiry inquiry = new SupportInquiry(
			resolveEmail(request.email(), user),
			trimRequired(request.subject(), "제목"),
			trimRequired(request.message(), "문의 내용"),
			resolveType(request.type()),
			user == null ? null : user.getId(),
			user == null ? null : user.getUsername(),
			remoteAddress,
			LocalDateTime.now()
		);

		validate(inquiry);
		rateLimiter.consume(rateLimitKey(inquiry), 1, RATE_LIMIT_WINDOW);
		notifier.notify(inquiry);
		return inquiry;
	}

	private String resolveEmail(String email, User user) {
		if (email != null && !email.isBlank()) {
			return email.trim();
		}
		if (user != null) {
			return user.getEmail();
		}
		throw new InvalidSupportInquiryException("이메일을 입력해 주세요.");
	}

	private SupportInquiryType resolveType(SupportInquiryType type) {
		if (type == null) {
			return SupportInquiryType.OTHER;
		}
		return type;
	}

	private void validate(SupportInquiry inquiry) {
		if (!EMAIL_PATTERN.matcher(inquiry.email()).matches()) {
			throw new InvalidSupportInquiryException("올바른 이메일을 입력해 주세요.");
		}
		if (inquiry.subject().length() > SUBJECT_MAX_LENGTH) {
			throw new InvalidSupportInquiryException("제목은 120자 이하로 입력해 주세요.");
		}
		if (inquiry.message().length() > MESSAGE_MAX_LENGTH) {
			throw new InvalidSupportInquiryException("문의 내용은 4000자 이하로 입력해 주세요.");
		}
	}

	private String trimRequired(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new InvalidSupportInquiryException(fieldName + "을 입력해 주세요.");
		}
		return value.trim();
	}

	private String rateLimitKey(SupportInquiry inquiry) {
		if (inquiry.userId() != null) {
			return "support:user:" + inquiry.userId();
		}
		return "support:ip:" + inquiry.remoteAddress();
	}
}
