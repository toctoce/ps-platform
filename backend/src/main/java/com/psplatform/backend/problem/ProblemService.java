package com.psplatform.backend.problem;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProblemService {

	private final ProblemRepository problemRepository;
	private final ProblemTestCaseRepository problemTestCaseRepository;
	private final ProblemExampleRepository problemExampleRepository;
	private final ProblemCategoryRepository problemCategoryRepository;

	public ProblemService(
		ProblemRepository problemRepository,
		ProblemTestCaseRepository problemTestCaseRepository,
		ProblemExampleRepository problemExampleRepository,
		ProblemCategoryRepository problemCategoryRepository
	) {
		this.problemRepository = problemRepository;
		this.problemTestCaseRepository = problemTestCaseRepository;
		this.problemExampleRepository = problemExampleRepository;
		this.problemCategoryRepository = problemCategoryRepository;
	}

	@Transactional
	public Problem create(ProblemCreateRequest request) {
		Problem problem = problemRepository.save(request);
		problemCategoryRepository.addLinks(problem.getId(), request.categories());
		if (request.testCases() != null) {
			problemTestCaseRepository.saveAll(problem.getId(), request.testCases());
		}
		if (request.examples() != null) {
			problemExampleRepository.saveAll(problem.getId(), request.examples());
		}
		return hydrateProblems(List.of(problem)).getFirst();
	}

	public List<Problem> findAll() {
		return hydrateCategories(problemRepository.findAll());
	}

	public List<Problem> findAll(String category) {
		if (category == null || category.isBlank()) {
			return findAll();
		}
		return hydrateCategories(problemRepository.findByCategory(category));
	}

	public List<Problem> findRandomProblems(int count) {
		int safeCount = Math.max(0, Math.min(count, 100));
		return hydrateCategories(problemRepository.findRandom(safeCount));
	}

	public Problem findById(long id) {
		Problem problem = problemRepository.findById(id)
			.orElseThrow(() -> new ProblemNotFoundException(id));
		return hydrateProblems(List.of(problem)).getFirst();
	}

	private List<Problem> hydrateProblems(List<Problem> problems) {
		List<Long> problemIds = problems.stream()
			.map(Problem::getId)
			.filter(id -> id != null)
			.toList();
		Map<Long, List<ProblemCategory>> categoryByProblemId = problemCategoryRepository.findByProblemIds(problemIds);
		return problems.stream()
			.map(problem -> problem.withCategories(categoryByProblemId.getOrDefault(problem.getId(), List.of())))
			.toList();
	}

	private List<Problem> hydrateCategories(List<Problem> problems) {
		return hydrateProblems(problems);
	}

	@Transactional
	public ProblemTestCase addTestCase(long problemId, ProblemTestCaseCreateRequest request) {
		findById(problemId);
		return problemTestCaseRepository.save(problemId, request);
	}

	public List<ProblemTestCase> findTestCasesByProblemId(long problemId) {
		findById(problemId);
		return problemTestCaseRepository.findByProblemId(problemId);
	}

	@Transactional
	public ProblemExample addExample(long problemId, ProblemExampleCreateRequest request) {
		findById(problemId);
		return problemExampleRepository.save(problemId, request);
	}

	public List<ProblemExample> findExamplesByProblemId(long problemId) {
		findById(problemId);
		return problemExampleRepository.findByProblemId(problemId);
	}
}
