package com.psplatform.backend.emailverification;

public class InvalidEmailVerificationException extends RuntimeException {

	public InvalidEmailVerificationException(String message) {
		super(message);
	}
}
