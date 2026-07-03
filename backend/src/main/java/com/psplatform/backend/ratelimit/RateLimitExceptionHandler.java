package com.psplatform.backend.ratelimit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RateLimitExceptionHandler {

	@ExceptionHandler(RateLimitExceededException.class)
	public ResponseEntity<ErrorResponse> handleRateLimitExceeded(RateLimitExceededException exception) {
		long retryAfterSeconds = exception.retryAfterSeconds();
		return ResponseEntity
			.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
			.body(new ErrorResponse(exception.getMessage(), retryAfterSeconds));
	}

	public record ErrorResponse(String message, long retryAfterSeconds) {
	}
}
