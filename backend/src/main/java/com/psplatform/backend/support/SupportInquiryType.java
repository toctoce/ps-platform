package com.psplatform.backend.support;

public enum SupportInquiryType {
	BUG("버그"),
	ACCOUNT("계정"),
	PROBLEM_OR_JUDGE("문제/채점"),
	OTHER("기타");

	private final String label;

	SupportInquiryType(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
