package com.psplatform.backend.report;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.psplatform.backend.problem.ProblemNotFoundException;
import com.psplatform.backend.problem.ProblemRepository;
import com.psplatform.backend.user.User;

@Service
public class ProblemReportService {

	private final ProblemRepository problemRepository;
	private final ProblemReportRepository problemReportRepository;

	public ProblemReportService(
		ProblemRepository problemRepository,
		ProblemReportRepository problemReportRepository
	) {
		this.problemRepository = problemRepository;
		this.problemReportRepository = problemReportRepository;
	}

	@Transactional
	public ProblemReport create(long problemId, User reporter, ProblemReportCreateRequest request) {
		if (problemRepository.findById(problemId).isEmpty()) {
			throw new ProblemNotFoundException(problemId);
		}
		validateCreateRequest(request);
		return problemReportRepository.save(problemId, reporter.getId(), request);
	}

	public List<ProblemReport> findAll(User admin) {
		assertAdmin(admin);
		return problemReportRepository.findAll();
	}

	@Transactional
	public ProblemReport review(long reportId, User admin, ProblemReportReviewRequest request) {
		assertAdmin(admin);
		if (request.status() == null) {
			throw new InvalidProblemReportException("Report status is required");
		}
		if (problemReportRepository.findById(reportId).isEmpty()) {
			throw new ProblemReportNotFoundException(reportId);
		}
		return problemReportRepository.updateReview(reportId, request.status(), request.adminNote());
	}

	private void validateCreateRequest(ProblemReportCreateRequest request) {
		if (request.type() == null) {
			throw new InvalidProblemReportException("Report type is required");
		}
		if (request.message() == null || request.message().isBlank()) {
			throw new InvalidProblemReportException("Report message is required");
		}
	}

	private void assertAdmin(User user) {
		if (!"ADMIN".equals(user.getRole())) {
			throw new ProblemReportForbiddenException();
		}
	}
}
