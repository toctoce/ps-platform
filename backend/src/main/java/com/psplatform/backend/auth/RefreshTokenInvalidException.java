package com.psplatform.backend.auth;

public class RefreshTokenInvalidException extends AuthException {
	public RefreshTokenInvalidException() {
		super("Invalid refresh token");
	}
}
