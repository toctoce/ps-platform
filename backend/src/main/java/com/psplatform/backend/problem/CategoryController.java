package com.psplatform.backend.problem;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

	private final ProblemService problemService;

	public CategoryController(ProblemService problemService) {
		this.problemService = problemService;
	}

	@GetMapping
	public List<ProblemCategorySummaryResponse> findAll() {
		return problemService.findCategories()
			.stream()
			.map(ProblemCategorySummaryResponse::from)
			.toList();
	}
}
