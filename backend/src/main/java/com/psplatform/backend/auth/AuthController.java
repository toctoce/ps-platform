package com.psplatform.backend.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.user.UserResponse;
import com.psplatform.backend.user.PasswordChangeRequest;
import com.psplatform.backend.user.UsernameUpdateRequest;
import com.psplatform.backend.experience.ExperienceService;
import com.psplatform.backend.user.User;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final ExperienceService experienceService;

	public AuthController(AuthService authService, ExperienceService experienceService) {
		this.authService = authService;
		this.experienceService = experienceService;
	}

	@PostMapping("/login")
	public ResponseEntity<AuthTokenResponse> login(@RequestBody AuthLoginRequest request) {
		AuthTokenPayload payload = authService.login(request);
		return ResponseEntity.ok(AuthTokenResponse.from(
			payload,
			experienceService.findProgress(payload.user().getId())
		));
	}

	@PostMapping("/refresh")
	public ResponseEntity<AuthTokenResponse> refresh(@RequestBody AuthRefreshRequest request) {
		AuthTokenPayload payload = authService.refresh(request.refreshToken());
		return ResponseEntity.ok(AuthTokenResponse.from(
			payload,
			experienceService.findProgress(payload.user().getId())
		));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestBody AuthRefreshRequest request) {
		authService.logout(request.refreshToken());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/me")
	public UserResponse me(@RequestHeader(value = "Authorization", required = false) String authorization) {
		User user = authService.verifyAccessToken(authorization);
		return UserResponse.from(user, experienceService.findProgress(user.getId()));
	}

	@PatchMapping("/me/username")
	public UserResponse updateUsername(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestBody UsernameUpdateRequest request
	) {
		User user = authService.updateCurrentUsername(authorization, request);
		return UserResponse.from(user, experienceService.findProgress(user.getId()));
	}

	@PatchMapping("/me/password")
	public ResponseEntity<Void> changePassword(
		@RequestHeader(value = "Authorization", required = false) String authorization,
		@RequestBody PasswordChangeRequest request
	) {
		authService.changeCurrentPassword(authorization, request);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/me")
	public ResponseEntity<Void> deleteMe(@RequestHeader(value = "Authorization", required = false) String authorization) {
		authService.deleteCurrentUser(authorization);
		return ResponseEntity.noContent().build();
	}
}
