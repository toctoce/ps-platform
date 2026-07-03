package com.psplatform.backend.support;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.psplatform.backend.auth.AuthService;
import com.psplatform.backend.user.User;

@RestController
public class SupportInquiryController {

	private final SupportInquiryService supportInquiryService;
	private final AuthService authService;

	public SupportInquiryController(SupportInquiryService supportInquiryService, AuthService authService) {
		this.supportInquiryService = supportInquiryService;
		this.authService = authService;
	}

	@PostMapping("/api/support/inquiries")
	public ResponseEntity<SupportInquiryResponse> create(
		@RequestBody SupportInquiryCreateRequest request,
		@RequestHeader(value = "Authorization", required = false) String authorization,
		HttpServletRequest servletRequest
	) {
		User user = authorization == null || authorization.isBlank()
			? null
			: authService.verifyAccessToken(authorization);
		supportInquiryService.create(request, user, servletRequest.getRemoteAddr());
		return ResponseEntity
			.accepted()
			.body(new SupportInquiryResponse("문의가 접수되었습니다."));
	}
}
