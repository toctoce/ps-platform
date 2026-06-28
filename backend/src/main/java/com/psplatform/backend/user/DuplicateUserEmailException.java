package com.psplatform.backend.user;

public class DuplicateUserEmailException extends RuntimeException {
	public DuplicateUserEmailException(String email) {
		super("Email already exists: " + email);
	}
}
