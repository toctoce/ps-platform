package com.psplatform.backend.user;

import java.util.regex.Pattern;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private static final String USER_ROLE = "USER";
	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final int MIN_PASSWORD_LENGTH = 8;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
		this.passwordEncoder = new BCryptPasswordEncoder();
	}

	public User signup(UserSignupRequest request) {
		String email = trimOrThrow(request.email(), "email");
		String username = trimOrThrow(request.username(), "username");
		String password = trimOrThrow(request.password(), "password");

		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new InvalidUserInputException("Invalid email format");
		}

		if (password.length() < MIN_PASSWORD_LENGTH) {
			throw new InvalidUserInputException("Password must be at least 8 characters");
		}

		if (userRepository.findByEmail(email).isPresent()) {
			throw new DuplicateUserEmailException(email);
		}

		if (userRepository.findByUsername(username).isPresent()) {
			throw new DuplicateUserUsernameException(username);
		}

		String passwordHash = passwordEncoder.encode(password);
		return userRepository.save(email, username, passwordHash, USER_ROLE);
	}

	public User findById(long id) {
		return userRepository.findById(id)
			.orElseThrow(() -> new UserNotFoundException(id));
	}

	private String trimOrThrow(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new InvalidUserInputException(fieldName + " is required");
		}
		return value.trim();
	}
}
