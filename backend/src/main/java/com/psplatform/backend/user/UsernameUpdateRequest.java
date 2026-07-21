package com.psplatform.backend.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UsernameUpdateRequest(
	@NotBlank(message = "username is required")
	@Pattern(regexp = UserUsername.INPUT_PATTERN, message = UserUsername.VALIDATION_MESSAGE)
	String username
) {
}
