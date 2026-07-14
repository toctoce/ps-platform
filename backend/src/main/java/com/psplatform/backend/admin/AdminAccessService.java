package com.psplatform.backend.admin;

import org.springframework.stereotype.Service;

import com.psplatform.backend.auth.AuthService;
import com.psplatform.backend.user.User;

@Service
public class AdminAccessService {

	private final AuthService authService;

	public AdminAccessService(AuthService authService) {
		this.authService = authService;
	}

	public User requireAdmin(String authorization) {
		User user = authService.verifyAccessToken(authorization);
		if (!"ADMIN".equals(user.getRole())) {
			throw new AdminForbiddenException();
		}
		return user;
	}
}
