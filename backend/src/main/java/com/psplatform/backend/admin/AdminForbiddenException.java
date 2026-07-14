package com.psplatform.backend.admin;

public class AdminForbiddenException extends RuntimeException {

	public AdminForbiddenException() {
		super("관리자 권한이 필요합니다.");
	}
}
