package com.psplatform.backend.auth;

import java.util.Collections;
import java.util.Map;

public record AuthLoginRequest(
	String provider,
	Map<String, String> credentials
) {

	public String provider() {
		return provider == null || provider.isBlank() ? "local" : provider;
	}

	public Map<String, String> credentials() {
		return credentials == null ? Collections.emptyMap() : credentials;
	}

	public String credential(String key) {
		return credentials().get(key);
	}
}
