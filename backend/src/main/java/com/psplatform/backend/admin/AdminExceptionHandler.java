package com.psplatform.backend.admin;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AdminExceptionHandler {

	@ExceptionHandler(AdminForbiddenException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public ErrorResponse handleForbidden(AdminForbiddenException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(AdminInvalidRequestException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidRequest(AdminInvalidRequestException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse handleConflict(DataIntegrityViolationException exception) {
		return new ErrorResponse("중복되거나 다른 데이터에서 사용 중인 값입니다.");
	}

	public record ErrorResponse(String message) {
	}
}
