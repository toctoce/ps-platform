package com.psplatform.backend.ratelimit;

import java.time.Duration;

public interface RateLimiter {

	void consume(String key, int limit, Duration window);
}
