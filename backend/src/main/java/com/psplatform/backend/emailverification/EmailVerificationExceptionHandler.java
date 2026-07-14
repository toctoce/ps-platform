package com.psplatform.backend.emailverification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EmailVerificationExceptionHandler {

	@ExceptionHandler(InvalidEmailVerificationException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalid(InvalidEmailVerificationException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(EmailVerificationRequestLimitException.class)
	@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
	public ErrorResponse handleRequestLimit(EmailVerificationRequestLimitException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(EmailVerificationDeliveryException.class)
	@ResponseStatus(HttpStatus.BAD_GATEWAY)
	public ErrorResponse handleDelivery(EmailVerificationDeliveryException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	public record ErrorResponse(String message) {
	}
}
