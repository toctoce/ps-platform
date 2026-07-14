package com.psplatform.backend.emailverification;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.psplatform.backend.user.DuplicateUserEmailException;
import com.psplatform.backend.user.UserEmail;
import com.psplatform.backend.user.UserRepository;

@Service
public class EmailVerificationService {

	private static final EmailVerificationPurpose SIGNUP = EmailVerificationPurpose.SIGNUP;
	private static final EmailVerificationPurpose PASSWORD_RESET = EmailVerificationPurpose.PASSWORD_RESET;
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final int VERIFICATION_CODE_BOUND = 1_000_000;
	private static final int TOKEN_BYTE_LENGTH = 32;

	private final EmailVerificationRepository verificationRepository;
	private final VerificationEmailSender emailSender;
	private final UserRepository userRepository;
	private final EmailVerificationConfig config;
	private final SecureRandom secureRandom = new SecureRandom();

	public EmailVerificationService(
		EmailVerificationRepository verificationRepository,
		VerificationEmailSender emailSender,
		UserRepository userRepository,
		EmailVerificationConfig config
	) {
		this.verificationRepository = verificationRepository;
		this.emailSender = emailSender;
		this.userRepository = userRepository;
		this.config = config;
	}

	public long requestSignupCode(String rawEmail) {
		String email = UserEmail.normalize(rawEmail);
		if (userRepository.findByEmail(email).isPresent()) {
			throw new DuplicateUserEmailException(email);
		}
		return requestCode(email, SIGNUP, true);
	}

	public long requestPasswordResetCode(String rawEmail) {
		String email = UserEmail.normalize(rawEmail);
		boolean shouldDeliver = userRepository.findByEmail(email).isPresent();
		return requestCode(email, PASSWORD_RESET, shouldDeliver);
	}

	private long requestCode(
		String email,
		EmailVerificationPurpose purpose,
		boolean shouldDeliver
	) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		verificationRepository.findByEmailAndPurpose(email, purpose)
			.filter(current -> current.requestedAt().plusSeconds(config.getResendCooldownSeconds()).isAfter(now))
			.ifPresent(current -> {
				throw new EmailVerificationRequestLimitException();
			});

		String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(VERIFICATION_CODE_BOUND));
		String codeHash = hashCode(email, purpose, code);
		OffsetDateTime expiresAt = now.plusSeconds(config.getCodeTtlSeconds());
		verificationRepository.saveOrReplace(email, purpose, codeHash, expiresAt, now);

		if (shouldDeliver) {
			try {
				emailSender.sendCode(email, code, purpose, config.getCodeTtlSeconds());
			} catch (RuntimeException exception) {
				verificationRepository.deleteByEmailAndPurposeAndCodeHash(email, purpose, codeHash);
				throw exception;
			}
		}
		return config.getCodeTtlSeconds();
	}

	public VerificationToken confirmSignupCode(String rawEmail, String rawCode) {
		return confirmCode(rawEmail, rawCode, SIGNUP);
	}

	public VerificationToken confirmPasswordResetCode(String rawEmail, String rawCode) {
		return confirmCode(rawEmail, rawCode, PASSWORD_RESET);
	}

	private VerificationToken confirmCode(
		String rawEmail,
		String rawCode,
		EmailVerificationPurpose purpose
	) {
		String email = UserEmail.normalize(rawEmail);
		String code = normalizeCode(rawCode);
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		EmailVerification current = verificationRepository.findByEmailAndPurpose(email, purpose)
			.orElseThrow(() -> new InvalidEmailVerificationException("먼저 인증번호를 요청해 주세요."));

		assertCodeCanBeVerified(current, now);
		String token = generateToken();
		String tokenHash = hashToken(token);
		OffsetDateTime tokenExpiresAt = now.plusSeconds(config.getTokenTtlSeconds());
		boolean verified = verificationRepository.verify(
			email,
			purpose,
			hashCode(email, purpose, code),
			tokenHash,
			tokenExpiresAt,
			now,
			config.getMaxAttempts()
		);
		if (!verified) {
			verificationRepository.incrementAttempt(current.id(), config.getMaxAttempts(), now);
			throw new InvalidEmailVerificationException("인증번호가 올바르지 않습니다.");
		}
		return new VerificationToken(token, config.getTokenTtlSeconds());
	}

	public void consumeSignupToken(String email, String token) {
		consumeToken(email, token, SIGNUP);
	}

	public void consumePasswordResetToken(String email, String token) {
		consumeToken(email, token, PASSWORD_RESET);
	}

	private void consumeToken(String email, String token, EmailVerificationPurpose purpose) {
		if (token == null || token.isBlank()) {
			throw new InvalidEmailVerificationException("이메일 인증이 필요합니다.");
		}
		boolean consumed = verificationRepository.consumeToken(
			email,
			purpose,
			hashToken(token.trim()),
			OffsetDateTime.now(ZoneOffset.UTC)
		);
		if (!consumed) {
			throw new InvalidEmailVerificationException("이메일 인증이 만료되었거나 유효하지 않습니다.");
		}
	}

	private void assertCodeCanBeVerified(EmailVerification verification, OffsetDateTime now) {
		if (verification.verifiedAt() != null) {
			throw new InvalidEmailVerificationException("이미 확인된 인증번호입니다. 다시 인증해 주세요.");
		}
		if (!verification.expiresAt().isAfter(now)) {
			throw new InvalidEmailVerificationException("인증번호가 만료되었습니다. 다시 요청해 주세요.");
		}
		if (verification.attemptCount() >= config.getMaxAttempts()) {
			throw new InvalidEmailVerificationException("인증 시도 횟수를 초과했습니다. 인증번호를 다시 요청해 주세요.");
		}
	}

	private String normalizeCode(String value) {
		if (value == null || !value.trim().matches("\\d{6}")) {
			throw new InvalidEmailVerificationException("6자리 인증번호를 입력해 주세요.");
		}
		return value.trim();
	}

	private String hashCode(String email, EmailVerificationPurpose purpose, String code) {
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(config.getCodeHashSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] value = "%s:%s:%s".formatted(purpose.name(), email, code).getBytes(StandardCharsets.UTF_8);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value));
		} catch (NoSuchAlgorithmException | InvalidKeyException exception) {
			throw new IllegalStateException("Email verification code hashing is unavailable", exception);
		}
	}

	private String hashToken(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is unavailable", exception);
		}
	}

	private String generateToken() {
		byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public record VerificationToken(String value, long expiresInSeconds) {
	}
}
