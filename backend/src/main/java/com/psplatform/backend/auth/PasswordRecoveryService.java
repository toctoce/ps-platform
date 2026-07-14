package com.psplatform.backend.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.emailverification.EmailVerificationService;
import com.psplatform.backend.user.InvalidUserInputException;
import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserEmail;
import com.psplatform.backend.user.UserRepository;

@Service
public class PasswordRecoveryService {

	private static final int MIN_PASSWORD_LENGTH = 8;

	private final EmailVerificationService emailVerificationService;
	private final UserRepository userRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

	public PasswordRecoveryService(
		EmailVerificationService emailVerificationService,
		UserRepository userRepository,
		RefreshTokenRepository refreshTokenRepository
	) {
		this.emailVerificationService = emailVerificationService;
		this.userRepository = userRepository;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Transactional
	public void resetPassword(PasswordResetRequest request) {
		String email = UserEmail.normalize(request.email());
		String newPassword = validatePassword(request.newPassword());
		emailVerificationService.consumePasswordResetToken(email, request.verificationToken());

		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new AuthException("비밀번호를 변경할 수 없습니다."));
		if (!userRepository.updatePasswordHash(user.getId(), passwordEncoder.encode(newPassword))) {
			throw new AuthException("비밀번호를 변경할 수 없습니다.");
		}
		refreshTokenRepository.revokeAllByUserId(user.getId());
	}

	private String validatePassword(String password) {
		if (password == null || password.isBlank()) {
			throw new InvalidUserInputException("password is required");
		}
		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new InvalidUserInputException("Password must be at least 8 characters");
		}
		return password;
	}
}
