package com.psplatform.backend.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserUsernameTest {

	@Test
	void acceptsKoreanAndEnglishUsernamesWithinTheirLengthRanges() {
		assertThat(UserUsername.validate("가나")).isEqualTo("가나");
		assertThat(UserUsername.validate("가나다라마바")).isEqualTo("가나다라마바");
		assertThat(UserUsername.validate("abc")).isEqualTo("abc");
		assertThat(UserUsername.validate("abcdefghijkl")).isEqualTo("abcdefghijkl");
	}

	@Test
	void rejectsInvalidLengthMixedLanguageAndSpecialCharacters() {
		assertInvalid("가");
		assertInvalid("가나다라마바사");
		assertInvalid("ab");
		assertInvalid("abcdefghijklm");
		assertInvalid("abc가");
		assertInvalid("abc-123");
		assertInvalid("abc def");
	}

	@Test
	void normalizesEnglishCaseForComparison() {
		assertThat(UserUsername.normalizeForComparison("Alice")).isEqualTo("alice");
	}

	private void assertInvalid(String username) {
		assertThatThrownBy(() -> UserUsername.validate(username))
			.isInstanceOf(InvalidUserInputException.class)
			.hasMessage(UserUsername.VALIDATION_MESSAGE);
	}
}
