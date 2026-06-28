package com.psplatform.backend.user;

import java.time.LocalDateTime;

public class User {

	private final Long id;
	private final String email;
	private final String username;
	private final String passwordHash;
	private final String role;
	private final LocalDateTime createdAt;
	private final LocalDateTime updatedAt;

	public User(
		Long id,
		String email,
		String username,
		String passwordHash,
		String role,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
	) {
		this.id = id;
		this.email = email;
		this.username = username;
		this.passwordHash = passwordHash;
		this.role = role;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}
}
