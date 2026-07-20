package com.psplatform.backend.user;

import java.time.Instant;

public class User {

	private final Long id;
	private final String email;
	private final String username;
	private final String passwordHash;
	private final String role;
	private final int authVersion;
	private final Instant createdAt;
	private final Instant updatedAt;
	private final Instant deletedAt;
	private final Instant suspendedAt;

	public User(
		Long id,
		String email,
		String username,
		String passwordHash,
		String role,
		int authVersion,
		Instant createdAt,
		Instant updatedAt,
		Instant deletedAt,
		Instant suspendedAt
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

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public boolean isDeleted() {
		return deletedAt != null;
	}

	public Instant getSuspendedAt() {
		return suspendedAt;
	}

	public boolean isSuspended() {
		return suspendedAt != null;
	}
}
