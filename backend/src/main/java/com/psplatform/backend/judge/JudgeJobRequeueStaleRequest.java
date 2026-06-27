package com.psplatform.backend.judge;

public record JudgeJobRequeueStaleRequest(
	long staleAfterSeconds
) {
}
