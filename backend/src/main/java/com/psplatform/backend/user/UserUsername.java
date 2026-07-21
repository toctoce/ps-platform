package com.psplatform.backend.user;

import java.util.Locale;
import java.util.regex.Pattern;

public final class UserUsername {

	public static final String INPUT_PATTERN = "(?:[가-힣]{2,6}|[A-Za-z]{3,12})";
	public static final String VALIDATION_MESSAGE = "닉네임은 한글 2~6자 또는 영문 3~12자로 입력해 주세요.";

	private static final Pattern USERNAME_PATTERN = Pattern.compile("^" + INPUT_PATTERN + "$");

	private UserUsername() {
	}

	public static String validate(String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidUserInputException("username is required");
		}
		if (!USERNAME_PATTERN.matcher(value).matches()) {
			throw new InvalidUserInputException(VALIDATION_MESSAGE);
		}
		return value;
	}

	public static String normalizeForComparison(String value) {
		return value.toLowerCase(Locale.ROOT);
	}
}
