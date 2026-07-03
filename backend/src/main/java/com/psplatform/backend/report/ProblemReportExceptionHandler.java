package com.psplatform.backend.report;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ProblemReportExceptionHandler {

	@ExceptionHandler(ProblemReportNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleNotFound(ProblemReportNotFoundException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(InvalidProblemReportException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalid(InvalidProblemReportException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(ProblemReportForbiddenException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public ErrorResponse handleForbidden(ProblemReportForbiddenException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	public record ErrorResponse(String message) {
	}
}
