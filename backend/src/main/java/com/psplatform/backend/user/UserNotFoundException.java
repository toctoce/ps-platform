package com.psplatform.backend.user;

public class UserNotFoundException extends RuntimeException {
	public UserNotFoundException(long userId) {
		super("User not found: " + userId);
	}
}
