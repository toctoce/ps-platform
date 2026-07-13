package com.psplatform.backend.submission;

public class GuestSubmissionLimitExceededException extends RuntimeException {

	public GuestSubmissionLimitExceededException() {
		super("비회원 제출 횟수를 모두 사용했습니다. 로그인 후 계속 제출해 주세요.");
	}
}
