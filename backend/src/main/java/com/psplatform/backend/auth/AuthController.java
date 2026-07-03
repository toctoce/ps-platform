package com.psplatform.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.user.UserResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ResponseEntity<AuthTokenResponse> login(@RequestBody AuthLoginRequest request) {
		return ResponseEntity.ok(AuthTokenResponse.from(authService.login(request)));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthTokenResponse> refresh(@RequestBody AuthRefreshRequest request) {
		return ResponseEntity.ok(AuthTokenResponse.from(authService.refresh(request.refreshToken())));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestBody AuthRefreshRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public UserResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
		return UserResponse.from(authService.verifyAccessToken(authorization));
	}

	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteMe(@RequestHeader(value = "Authorization", required = false) String authorization) {
		authService.deleteCurrentUser(authorization);
		return ResponseEntity.noContent().build();
	}
}
