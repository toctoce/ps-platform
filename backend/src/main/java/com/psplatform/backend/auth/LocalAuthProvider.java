package com.psplatform.backend.auth;

import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import com.psplatform.backend.user.User;
import com.psplatform.backend.user.UserRepository;

@Component
public class LocalAuthProvider implements AuthProvider {

	private final UserRepository userRepository;
	private final BCryptPasswordEncoder passwordEncoder;

	public LocalAuthProvider(UserRepository userRepository) {
		this.userRepository = userRepository;
		this.passwordEncoder = new BCryptPasswordEncoder();
	}

	@Override
	public String provider() {
		return "local";
	}

	@Override
	public User authenticate(AuthLoginRequest request) {
		Map<String, String> credentials = request.credentials();
		String email = credentials.get("email");
		String password = credentials.get("password");

		if (isBlank(email) || isBlank(password)) {
			throw new InvalidCredentialException();
		}

		User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
			.orElseThrow(InvalidCredentialException::new);

		if (user.isDeleted() || user.isSuspended()) {
			throw new InvalidCredentialException();
		}

		if (!passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new InvalidCredentialException();
		}

		return user;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
