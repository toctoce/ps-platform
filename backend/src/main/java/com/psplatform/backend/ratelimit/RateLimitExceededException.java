package com.psplatform.backend.ratelimit;

import java.time.Duration;

public class RateLimitExceededException extends RuntimeException {

	private final Duration retryAfter;

	public RateLimitExceededException(Duration retryAfter) {
		super("요청이 너무 많습니다. 잠시 후 다시 시도해주세요.");
		this.retryAfter = retryAfter;
	}

	public long retryAfterSeconds() {
		long millis = Math.max(1, retryAfter.toMillis());
		return (millis + 999) / 1000;
	}
}
