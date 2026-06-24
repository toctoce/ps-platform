package com.psplatform.backend.problem;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemExceptionHandler {

	@ExceptionHandler(ProblemNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleProblemNotFound(ProblemNotFoundException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	public record ErrorResponse(String message) {
	}
}
