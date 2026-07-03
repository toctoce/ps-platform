package com.psplatform.backend.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class InMemoryRateLimiter implements RateLimiter {

	private final Clock clock;
	private final Map<String, Bucket> buckets = new HashMap<>();

	public InMemoryRateLimiter() {
		this(Clock.systemUTC());
	}

	InMemoryRateLimiter(Clock clock) {
		this.clock = clock;
	}

	@Override
	public synchronized void consume(String key, int limit, Duration window) {
		Instant now = clock.instant();
		Bucket bucket = buckets.get(key);
		if (bucket == null || !bucket.isWithinWindow(now, window)) {
			buckets.put(key, new Bucket(now, 1));
			return;
		}

		if (bucket.count() >= limit) {
			throw new RateLimitExceededException(window.minus(Duration.between(bucket.windowStartedAt(), now)));
		}

		buckets.put(key, new Bucket(bucket.windowStartedAt(), bucket.count() + 1));
	}

	private record Bucket(Instant windowStartedAt, int count) {

		private boolean isWithinWindow(Instant now, Duration window) {
			return Duration.between(windowStartedAt, now).compareTo(window) < 0;
		}
	}
}
