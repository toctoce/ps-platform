package com.psplatform.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler {

	@ExceptionHandler(UserNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public ErrorResponse handleUserNotFound(UserNotFoundException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler({DuplicateUserEmailException.class, DuplicateUserUsernameException.class})
	@ResponseStatus(HttpStatus.CONFLICT)
	public ErrorResponse handleUserConflict(RuntimeException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(InvalidUserInputException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidInput(InvalidUserInputException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleInvalidRequest(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream()
			.findFirst()
			.map(error -> error.getDefaultMessage())
			.orElse("Invalid request");
		return new ErrorResponse(message);
	}

	public record ErrorResponse(String message) {
	}
}
