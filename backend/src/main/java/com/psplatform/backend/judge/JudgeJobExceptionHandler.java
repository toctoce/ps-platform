package com.psplatform.backend.judge;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class JudgeJobExceptionHandler {

	@ExceptionHandler(JudgeJobNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleJudgeJobNotFound(JudgeJobNotFoundException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	public record ErrorResponse(String message) {
	}
}
