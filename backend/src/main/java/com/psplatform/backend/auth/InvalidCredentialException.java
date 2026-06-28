package com.psplatform.backend.auth;

public class InvalidCredentialException extends AuthException {
	public InvalidCredentialException() {
		super("Invalid email or password");
	}
}
