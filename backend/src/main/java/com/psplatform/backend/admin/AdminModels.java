package com.psplatform.backend.admin;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import com.psplatform.backend.problem.ProblemDifficulty;

public final class AdminModels {

	private AdminModels() {
	}

	public record Dashboard(
		long activeProblems,
		long activeUsers,
		long submissions,
		long pendingReports,
		long queuedJudgeJobs,
		long runningJudgeJobs
	) {
	}

	public record ProblemSummary(
		long id,
		String title,
		ProblemDifficulty difficulty,
		long exampleCount,
		long testCaseCount,
		long submissionCount,
		LocalDateTime createdAt,
		LocalDateTime archivedAt
	) {
	}

	public record ProblemDetail(
		long id,
		String title,
		String description,
		String solution,
		String inputDescription,
		String inputConstraints,
		String outputDescription,
		int timeLimitMs,
		int memoryLimitMb,
		ProblemDifficulty difficulty,
		List<String> categories,
		List<Example> examples,
		List<TestCase> testCases,
		LocalDateTime createdAt,
		LocalDateTime archivedAt
	) {
	}

	public record ProblemUpdateRequest(
		String title,
		String description,
		String solution,
		String inputDescription,
		String inputConstraints,
		String outputDescription,
		int timeLimitMs,
		int memoryLimitMb,
		ProblemDifficulty difficulty,
		List<String> categories
	) {
	}

	public record TestCase(long id, String input, String expectedOutput, int sortOrder) {
	}

	public record Example(long id, String input, String expectedOutput, String description, int sortOrder) {
	}

	public record UserSummary(
		long id,
		String email,
		String username,
		String role,
		long solvedProblemCount,
		long submissionCount,
		long acceptedSubmissionCount,
		LocalDateTime createdAt,
		LocalDateTime suspendedAt,
		LocalDateTime deletedAt
	) {
	}

	public record UserUpdateRequest(String role, boolean suspended) {
	}

	public record SubmissionSummary(
		long id,
		long problemId,
		String problemTitle,
		Long userId,
		String username,
		String language,
		String status,
		String verdict,
		Integer executionTimeMs,
		Integer memoryMb,
		Instant createdAt,
		Instant judgedAt
	) {
	}

	public record JudgeJobSummary(
		long id,
		String jobKind,
		Long submissionId,
		Long problemId,
		String status,
		String verdict,
		LocalDateTime createdAt,
		LocalDateTime pickedAt,
		LocalDateTime finishedAt
	) {
	}

	public record CategorySummary(
		long id,
		String name,
		int displayOrder,
		boolean active,
		long problemCount
	) {
	}

	public record CategoryUpdateRequest(String name, int displayOrder, boolean active) {
	}

	public record AuditLog(
		long id,
		long adminUserId,
		String adminUsername,
		String action,
		String targetType,
		String targetId,
		String detail,
		LocalDateTime createdAt
	) {
	}
}
