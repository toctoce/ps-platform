package com.psplatform.backend.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailSupportInquiryNotifier implements SupportInquiryNotifier {

	private final JavaMailSender mailSender;
	private final String toEmail;
	private final String fromEmail;
	private final String smtpUsername;

	public EmailSupportInquiryNotifier(
		JavaMailSender mailSender,
		@Value("${app.support.email.to-email}") String toEmail,
		@Value("${app.support.email.from-email:}") String fromEmail,
		@Value("${spring.mail.username:}") String smtpUsername
	) {
		this.mailSender = mailSender;
		this.toEmail = toEmail;
		this.fromEmail = fromEmail;
		this.smtpUsername = smtpUsername;
	}

	@Override
	public void notify(SupportInquiry inquiry) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(toEmail);
		String resolvedFrom = firstPresent(fromEmail, smtpUsername);
		if (!resolvedFrom.isBlank()) {
			message.setFrom(resolvedFrom);
		}
		message.setReplyTo(inquiry.email());
		message.setSubject("[알뿌 문의] [%s] %s".formatted(inquiry.type().label(), inquiry.subject()));
		message.setText(body(inquiry));

		try {
			mailSender.send(message);
		} catch (MailException exception) {
			throw new SupportInquiryDeliveryException(exception);
		}
	}

	private String body(SupportInquiry inquiry) {
		return """
			문의 종류: %s
			제목: %s
			답장 이메일: %s
			사용자 ID: %s
			닉네임: %s
			IP: %s
			접수 시각: %s

			내용:
			%s
			""".formatted(
			inquiry.type().label(),
			inquiry.subject(),
			inquiry.email(),
			inquiry.userId() == null ? "-" : inquiry.userId(),
			blankToDash(inquiry.username()),
			blankToDash(inquiry.remoteAddress()),
			inquiry.createdAt(),
			inquiry.message()
		);
	}

	private String firstPresent(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first.trim();
		}
		if (second != null && !second.isBlank()) {
			return second.trim();
		}
		return "";
	}

	private String blankToDash(String value) {
		if (value == null || value.isBlank()) {
			return "-";
		}
		return value;
	}
}
