package com.psplatform.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler(InvalidCredentialException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleInvalidCredential(InvalidCredentialException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler({
		UnsupportedAuthProviderException.class,
		InvalidAccessTokenException.class,
		RefreshTokenInvalidException.class,
		RefreshTokenExpiredException.class
	})
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public ErrorResponse handleAuthTokenErrors(AuthException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	@ExceptionHandler(AuthException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public ErrorResponse handleAuthError(AuthException exception) {
		return new ErrorResponse(exception.getMessage());
	}

	public record ErrorResponse(String message) {
	}
}
