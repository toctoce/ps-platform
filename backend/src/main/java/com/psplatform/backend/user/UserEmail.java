package com.psplatform.backend.user;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UserEmail {

	private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private UserEmail() {
	}

	public static String normalize(String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidUserInputException("email is required");
		}

		String email = value.trim().toLowerCase(Locale.ROOT);
		if (!EMAIL_PATTERN.matcher(email).matches()) {
			throw new InvalidUserInputException("Invalid email format");
		}
		return email;
	}
}
