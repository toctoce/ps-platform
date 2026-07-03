package com.psplatform.backend.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

	@Test
	void consumeRejectsRequestsOverLimitInsideWindow() {
		MutableClock clock = new MutableClock(Instant.parse("2026-07-03T00:00:00Z"));
		InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

		rateLimiter.consume("run:user:1", 1, Duration.ofSeconds(1));

		assertThatThrownBy(() -> rateLimiter.consume("run:user:1", 1, Duration.ofSeconds(1)))
			.isInstanceOf(RateLimitExceededException.class);
	}

	@Test
	void consumeAllowsRequestsAfterWindowExpires() {
		MutableClock clock = new MutableClock(Instant.parse("2026-07-03T00:00:00Z"));
		InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter(clock);

		rateLimiter.consume("run:user:1", 1, Duration.ofSeconds(1));
		clock.advance(Duration.ofSeconds(1));

		rateLimiter.consume("run:user:1", 1, Duration.ofSeconds(1));
	}

	private static class MutableClock extends Clock {

		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
