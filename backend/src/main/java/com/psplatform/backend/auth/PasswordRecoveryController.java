package com.psplatform.backend.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.emailverification.EmailVerificationConfirmResponse;
import com.psplatform.backend.emailverification.EmailVerificationService;

@RestController
@RequestMapping("/api/password-recovery")
public class PasswordRecoveryController {

	private final EmailVerificationService emailVerificationService;
	private final PasswordRecoveryService passwordRecoveryService;

	public PasswordRecoveryController(
		EmailVerificationService emailVerificationService,
		PasswordRecoveryService passwordRecoveryService
	) {
		this.emailVerificationService = emailVerificationService;
		this.passwordRecoveryService = passwordRecoveryService;
	}

	@PostMapping("/verifications")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public PasswordRecoveryVerificationResponse requestVerification(
		@RequestBody PasswordRecoveryVerificationRequest request
	) {
		long expiresInSeconds = emailVerificationService.requestPasswordResetCode(request.email());
		return new PasswordRecoveryVerificationResponse(
			"입력한 이메일로 인증번호를 전송했습니다.",
			expiresInSeconds
		);
	}

	@PostMapping("/verifications/confirm")
	public EmailVerificationConfirmResponse confirmVerification(
		@RequestBody PasswordRecoveryConfirmRequest request
	) {
		EmailVerificationService.VerificationToken token = emailVerificationService.confirmPasswordResetCode(
			request.email(),
			request.code()
		);
		return new EmailVerificationConfirmResponse(token.value(), token.expiresInSeconds());
	}

	@PostMapping("/reset")
	public PasswordRecoveryResponse resetPassword(@RequestBody PasswordResetRequest request) {
		passwordRecoveryService.resetPassword(request);
		return new PasswordRecoveryResponse("비밀번호가 변경되었습니다.");
	}
}
