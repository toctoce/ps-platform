package com.psplatform.backend.auth;

import com.psplatform.backend.user.User;

public interface AuthProvider {

	String provider();

	User authenticate(AuthLoginRequest request);
}
