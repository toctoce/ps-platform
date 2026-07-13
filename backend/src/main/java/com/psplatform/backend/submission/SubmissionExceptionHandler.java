package com.psplatform.backend.submission;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class SubmissionExceptionHandler {

	@ExceptionHandler(SubmissionNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleSubmissionNotFound(SubmissionNotFoundException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(GuestSubmissionLimitExceededException.class)
	@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
	public ErrorResponse handleGuestSubmissionLimit(GuestSubmissionLimitExceededException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	public record ErrorResponse(String message) {
	}
}
