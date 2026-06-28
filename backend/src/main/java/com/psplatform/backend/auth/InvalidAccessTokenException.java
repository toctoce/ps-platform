package com.psplatform.backend.auth;

public class InvalidAccessTokenException extends AuthException {
	public InvalidAccessTokenException(String message) {
		super(message);
	}
}
