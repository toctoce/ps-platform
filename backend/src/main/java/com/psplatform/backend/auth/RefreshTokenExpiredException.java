package com.psplatform.backend.auth;

public class RefreshTokenExpiredException extends AuthException {
	public RefreshTokenExpiredException() {
		super("Refresh token expired");
	}
}
