package com.psplatform.backend.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class SubmissionResponseTest {

	@Test
	void convertsDatabaseTimestampsToUtcInstants() {
		Submission submission = new Submission(
			1L,
			2L,
			3L,
			"user",
			"java",
			"public class Main {}",
			SubmissionStatus.JUDGED,
			SubmissionVerdict.ACCEPTED,
			null,
			10,
			32,
			LocalDateTime.of(2026, 7, 13, 1, 2, 3),
			LocalDateTime.of(2026, 7, 13, 1, 2, 4)
		);

		SubmissionResponse response = SubmissionResponse.from(submission);

		assertThat(response.createdAt()).isEqualTo(Instant.parse("2026-07-13T01:02:03Z"));
		assertThat(response.judgedAt()).isEqualTo(Instant.parse("2026-07-13T01:02:04Z"));
	}

	@Test
	void keepsMissingJudgedTimestampNull() {
		Submission submission = new Submission(
			1L,
			2L,
			"java",
			"public class Main {}",
			SubmissionStatus.PENDING,
			null,
			null,
			LocalDateTime.of(2026, 7, 13, 1, 2, 3),
			null
		);

		SubmissionResponse response = SubmissionResponse.from(submission);

		assertThat(response.judgedAt()).isNull();
	}
}
