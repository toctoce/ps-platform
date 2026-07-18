package com.psplatform.backend.user;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.emailverification.EmailVerificationService;

@Service
public class UserService {

	private static final String USER_ROLE = "USER";
	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;
	private final EmailVerificationService emailVerificationService;
	private final PasswordEncoder passwordEncoder;

	public UserService(
		UserRepository userRepository,
		EmailVerificationService emailVerificationService
	) {
		this.userRepository = userRepository;
		this.emailVerificationService = emailVerificationService;
		this.passwordEncoder = new BCryptPasswordEncoder();
	}

	@Transactional
	public User signup(UserSignupRequest request) {
		String email = UserEmail.normalize(request.email());
		String username = trimOrThrow(request.username(), "username");
		String password = trimOrThrow(request.password(), "password");

		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new InvalidUserInputException("Password must be at least 8 characters");
		}

		if (userRepository.findByEmail(email).isPresent()) {
			throw new DuplicateUserEmailException(email);
		}

		if (userRepository.findByUsername(username).isPresent()) {
			throw new DuplicateUserUsernameException(username);
		}

		emailVerificationService.consumeSignupToken(email, request.verificationToken());
		String passwordHash = passwordEncoder.encode(password);
		try {
			return userRepository.save(email, username, passwordHash, USER_ROLE);
		} catch (DuplicateKeyException exception) {
			if (userRepository.findByEmail(email).isPresent()) {
				throw new DuplicateUserEmailException(email);
			}
			if (userRepository.findByUsername(username).isPresent()) {
				throw new DuplicateUserUsernameException(username);
			}
			throw exception;
		}
	}

	public User findById(long id) {
		return userRepository.findById(id)
			.orElseThrow(() -> new UserNotFoundException(id));
	}

	@Transactional
	public User updateUsername(User user, UsernameUpdateRequest request) {
		String username = trimOrThrow(request.username(), "username");
		userRepository.findByUsername(username)
			.filter(existing -> !existing.getId().equals(user.getId()))
			.ifPresent(existing -> {
				throw new DuplicateUserUsernameException(username);
			});

		try {
			if (!userRepository.updateUsername(user.getId(), username)) {
				throw new UserNotFoundException(user.getId());
			}
		} catch (DuplicateKeyException exception) {
			throw new DuplicateUserUsernameException(username);
		}
		return findById(user.getId());
	}

	@Transactional
	public void changePassword(User user, PasswordChangeRequest request) {
		String currentPassword = trimOrThrow(request.currentPassword(), "currentPassword");
		String newPassword = trimOrThrow(request.newPassword(), "newPassword");
		if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
			throw new InvalidUserInputException("현재 비밀번호가 올바르지 않습니다.");
		}
		if (newPassword.length() < MIN_PASSWORD_LENGTH) {
			throw new InvalidUserInputException("Password must be at least 8 characters");
		}
		if (!userRepository.updatePasswordHash(user.getId(), passwordEncoder.encode(newPassword))) {
			throw new UserNotFoundException(user.getId());
		}
	}

	private String trimOrThrow(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new InvalidUserInputException(fieldName + " is required");
		}
		return value.trim();
	}
}
