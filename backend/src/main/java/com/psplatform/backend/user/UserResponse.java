package com.psplatform.backend.user;

import java.time.Instant;

import com.psplatform.backend.experience.UserLevelProgress;

public record UserResponse(
	Long id,
	String email,
	String username,
	String role,
	Instant createdAt,
	Instant updatedAt,
	int level,
	long totalExperience,
	long currentLevelExperience,
	int requiredExperience,
	int progressPercent
) {

	public static UserResponse from(User user, UserLevelProgress progress) {
		if (user.isDeleted()) {
			return new UserResponse(
				user.getId(),
				"deleted",
				"탈퇴한 사용자",
				user.getRole(),
				user.getCreatedAt(),
				user.getUpdatedAt(),
				progress.level(),
				progress.totalExperience(),
				progress.currentLevelExperience(),
				progress.requiredExperience(),
				progress.progressPercent()
			);
		}
		return new UserResponse(
			user.getId(),
			user.getEmail(),
			user.getUsername(),
			user.getRole(),
			user.getCreatedAt(),
			user.getUpdatedAt(),
			progress.level(),
			progress.totalExperience(),
			progress.currentLevelExperience(),
			progress.requiredExperience(),
			progress.progressPercent()
		);
	}
}
