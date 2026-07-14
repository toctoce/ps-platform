package com.psplatform.backend.user;

import java.time.LocalDateTime;

public class User {

	private final Long id;
	private final String email;
	private final String username;
	private final String passwordHash;
	private final String role;
	private final int authVersion;
	private final LocalDateTime createdAt;
	private final LocalDateTime updatedAt;
	private final LocalDateTime deletedAt;
	private final LocalDateTime suspendedAt;

	public User(
		Long id,
		String email,
		String username,
		String passwordHash,
		String role,
		int authVersion,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		LocalDateTime deletedAt,
		LocalDateTime suspendedAt
	) {
		this.id = id;
		this.email = email;
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.authVersion = authVersion;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
		this.deletedAt = deletedAt;
		this.suspendedAt = suspendedAt;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getUsername() {
		return username;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getRole() {
		return role;
	}

	public int getAuthVersion() {
		return authVersion;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public LocalDateTime getDeletedAt() {
		return deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public LocalDateTime getSuspendedAt() {
		return suspendedAt;
	}

	public boolean isSuspended() {
		return suspendedAt != null;
	}
}
