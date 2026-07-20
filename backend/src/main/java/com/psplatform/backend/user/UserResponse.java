package com.psplatform.backend.user;

import java.time.Instant;

public record UserResponse(
	Long id,
	String email,
	String username,
	String role,
	Instant createdAt,
	Instant updatedAt
) {

	public static UserResponse from(User user) {
		if (user.isDeleted()) {
			return new UserResponse(
				user.getId(),
				"deleted",
				"탈퇴한 사용자",
				user.getRole(),
				user.getCreatedAt(),
				user.getUpdatedAt()
			);
		}
		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getUsername(),
			user.getRole(),
			user.getCreatedAt(),
			user.getUpdatedAt()
		);
	}
}
