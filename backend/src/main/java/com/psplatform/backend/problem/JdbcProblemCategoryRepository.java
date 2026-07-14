package com.psplatform.backend.problem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProblemCategoryRepository implements ProblemCategoryRepository {

	private final JdbcTemplate jdbcTemplate;

	public JdbcProblemCategoryRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void addLinks(long problemId, List<String> categoryNames) {
		if (categoryNames == null || categoryNames.isEmpty()) {
			return;
		}

		for (String rawName : categoryNames) {
			String categoryName = normalize(rawName);
			if (categoryName.isEmpty()) {
				continue;
			}
			long categoryId = ensureCategory(categoryName);
			jdbcTemplate.update(
				"""
				INSERT INTO problem_category_links (problem_id, category_id)
				SELECT ?, ?
				WHERE NOT EXISTS (
				  SELECT 1
				  FROM problem_category_links
				  WHERE problem_id = ? AND category_id = ?
				)
				""",
				ps -> {
					ps.setLong(1, problemId);
					ps.setLong(2, categoryId);
					ps.setLong(3, problemId);
					ps.setLong(4, categoryId);
				}
			);
		}
	}

	@Override
	public List<ProblemCategory> findAll() {
		return jdbcTemplate.query(
			"""
			SELECT id, name
			FROM problem_categories
			WHERE active = TRUE
			ORDER BY display_order ASC, name ASC
			""",
			(rs, rowNum) -> new ProblemCategory(
				rs.getLong("id"),
				rs.getString("name")
			)
		);
	}

	@Override
	public List<ProblemCategorySummary> findAllWithProblemCount() {
		return jdbcTemplate.query(
			"""
			SELECT pc.id, pc.name, COUNT(p.id) AS problem_count
			FROM problem_categories pc
			LEFT JOIN problem_category_links pcl ON pcl.category_id = pc.id
			LEFT JOIN problems p ON p.id = pcl.problem_id AND p.archived_at IS NULL
			WHERE pc.active = TRUE
			GROUP BY pc.id, pc.name
			ORDER BY MIN(pc.display_order) ASC, pc.name ASC
			""",
			(rs, rowNum) -> new ProblemCategorySummary(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getLong("problem_count")
			)
		);
	}

	@Override
	public Map<Long, List<ProblemCategory>> findByProblemIds(List<Long> problemIds) {
		if (problemIds == null || problemIds.isEmpty()) {
			return Map.of();
		}

		String placeholders = problemIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
		String sql = """
			SELECT pcl.problem_id, pc.id, pc.name
			FROM problem_category_links pcl
			JOIN problem_categories pc ON pc.id = pcl.category_id
			WHERE pcl.problem_id IN (%s) AND pc.active = TRUE
			ORDER BY pcl.problem_id, pc.display_order, pc.name
			""".formatted(placeholders);

		return jdbcTemplate.query(sql, (ResultSetExtractor<Map<Long, List<ProblemCategory>>>) rs -> {
			Map<Long, List<ProblemCategory>> map = new LinkedHashMap<>();
			while (rs.next()) {
				long problemId = rs.getLong("problem_id");
				map.computeIfAbsent(problemId, ignored -> new ArrayList<>())
					.add(new ProblemCategory(
						rs.getLong("id"),
						rs.getString("name")
					));
			}
			return map;
		}, problemIds.toArray());
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM problem_category_links");
		jdbcTemplate.update("DELETE FROM problem_categories");
	}

	private long ensureCategory(String categoryName) {
		try {
			return jdbcTemplate.queryForObject(
				"SELECT id FROM problem_categories WHERE name = ?",
				Long.class,
				categoryName
			);
		} catch (EmptyResultDataAccessException ignored) {
			jdbcTemplate.update(
				"INSERT INTO problem_categories (name) VALUES (?)",
				categoryName
			);
			return queryCategoryId(categoryName);
		}
	}

	private long queryCategoryId(String categoryName) {
		return jdbcTemplate.queryForObject("SELECT id FROM problem_categories WHERE name = ?", Long.class, categoryName);
	}

	private String normalize(String categoryName) {
		return categoryName == null ? "" : categoryName.trim();
	}
}
