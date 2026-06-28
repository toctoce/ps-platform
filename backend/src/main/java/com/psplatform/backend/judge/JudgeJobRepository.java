package com.psplatform.backend.judge;

import java.util.Optional;

import com.psplatform.backend.submission.Submission;
import com.psplatform.backend.submission.SubmissionVerdict;

public interface JudgeJobRepository {

	void create(long submissionId);

	Optional<JudgeJobAssignment> findNextAndMarkRunning();

	Submission complete(long jobId, SubmissionVerdict verdict, String judgeMessage);

	int requeueStale(long staleAfterSeconds);

	int countBySubmissionId(long submissionId);
}
