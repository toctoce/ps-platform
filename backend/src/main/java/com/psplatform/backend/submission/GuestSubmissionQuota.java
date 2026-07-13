package com.psplatform.backend.submission;

public record GuestSubmissionQuota(
	String guestSessionId,
	int remaining,
	boolean newSession
) {
}
