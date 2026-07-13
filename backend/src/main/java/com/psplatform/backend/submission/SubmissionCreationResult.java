package com.psplatform.backend.submission;

public record SubmissionCreationResult(
	Submission submission,
	GuestSubmissionQuota guestQuota
) {
}
