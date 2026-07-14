package com.psplatform.backend.emailverification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/email-verifications")
public class EmailVerificationController {

	private final EmailVerificationService emailVerificationService;

	public EmailVerificationController(EmailVerificationService emailVerificationService) {
		this.emailVerificationService = emailVerificationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.ACCEPTED)
	public EmailVerificationRequestResponse requestCode(@RequestBody EmailVerificationRequest request) {
		long expiresInSeconds = emailVerificationService.requestSignupCode(request.email());
		return new EmailVerificationRequestResponse("인증번호를 전송했습니다.", expiresInSeconds);
	}

	@PostMapping("/confirm")
	public EmailVerificationConfirmResponse confirmCode(
		@RequestBody EmailVerificationConfirmRequest request
	) {
		EmailVerificationService.VerificationToken token = emailVerificationService.confirmSignupCode(
			request.email(),
			request.code()
		);
		return new EmailVerificationConfirmResponse(token.value(), token.expiresInSeconds());
	}
}
