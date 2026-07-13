package com.psplatform.backend.submission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class SubmissionResponseTest {

	@Test
	void preservesSubmissionInstants() {
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
			Instant.parse("2026-07-13T01:02:03Z"),
			Instant.parse("2026-07-13T01:02:04Z")
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
			Instant.parse("2026-07-13T01:02:03Z"),
			null
		);

		SubmissionResponse response = SubmissionResponse.from(submission);

		assertThat(response.judgedAt()).isNull();
	}
}
