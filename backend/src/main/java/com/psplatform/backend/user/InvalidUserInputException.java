package com.psplatform.backend.user;

public class InvalidUserInputException extends RuntimeException {
	public InvalidUserInputException(String message) {
		super(message);
	}
}
