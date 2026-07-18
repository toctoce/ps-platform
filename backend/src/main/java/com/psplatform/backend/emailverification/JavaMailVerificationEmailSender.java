package com.psplatform.backend.emailverification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class JavaMailVerificationEmailSender implements VerificationEmailSender {

	private static final Logger log = LoggerFactory.getLogger(JavaMailVerificationEmailSender.class);

	private final JavaMailSender mailSender;
	private final String fromEmail;
	private final String smtpUsername;

	public JavaMailVerificationEmailSender(
		JavaMailSender mailSender,
		@Value("${app.email-verification.from-email:}") String fromEmail,
		@Value("${spring.mail.username:}") String smtpUsername
	) {
		this.mailSender = mailSender;
		this.fromEmail = fromEmail;
		this.smtpUsername = smtpUsername;
	}

	@Override
	public void sendCode(
		String email,
		String code,
		EmailVerificationPurpose purpose,
		long expiresInSeconds
	) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(email);
		String resolvedFrom = firstPresent(fromEmail, smtpUsername);
		if (!resolvedFrom.isBlank()) {
			message.setFrom(resolvedFrom);
		}
		message.setSubject(subject(purpose));
		message.setText("""
			알뿌 %s 인증번호입니다.

			인증번호: %s

			인증번호는 %d분 동안 유효합니다.
			본인이 요청하지 않았다면 이 메일을 무시해 주세요.
			""".formatted(purposeLabel(purpose), code, Math.max(1, expiresInSeconds / 60)));

		try {
			mailSender.send(message);
		} catch (MailException exception) {
			log.error(
				"이메일 인증 메일 전송 실패: purpose=" + purpose + ", recipient=" + maskEmail(email),
				exception
			);
			throw new EmailVerificationDeliveryException(exception);
		}
	}

	private String subject(EmailVerificationPurpose purpose) {
		return "[알뿌] " + purposeLabel(purpose) + " 이메일 인증번호";
	}

	private String purposeLabel(EmailVerificationPurpose purpose) {
		return switch (purpose) {
			case SIGNUP -> "회원가입";
			case PASSWORD_RESET -> "비밀번호 찾기";
		};
	}

	private String maskEmail(String email) {
		if (email == null || email.isBlank()) {
			return "-";
		}
		int atIndex = email.indexOf('@');
		if (atIndex <= 0) {
			return "***";
		}
		return email.charAt(0) + "***" + email.substring(atIndex);
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
}
