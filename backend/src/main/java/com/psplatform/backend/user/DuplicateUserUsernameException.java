package com.psplatform.backend.user;

public class DuplicateUserUsernameException extends RuntimeException {
	public DuplicateUserUsernameException(String username) {
		super("Username already exists: " + username);
	}
}
