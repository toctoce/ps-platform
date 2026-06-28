package com.psplatform.backend.auth;

public class UnsupportedAuthProviderException extends AuthException {
	public UnsupportedAuthProviderException(String provider) {
		super("Unsupported auth provider: " + provider);
	}
}
