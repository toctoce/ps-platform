package com.psplatform.backend.experience;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcExperienceAwardRepository implements ExperienceAwardRepository {

	private final JdbcTemplate jdbcTemplate;
	private final boolean postgresql;

	public JdbcExperienceAwardRepository(JdbcTemplate jdbcTemplate, DataSource dataSource) {
		this.jdbcTemplate = jdbcTemplate;
		this.postgresql = isPostgresql(dataSource);
	}

	@Override
	public boolean award(long userId, long problemId, int experience) {
		if (!postgresql) {
			return awardForTestDatabase(userId, problemId, experience);
		}
		return jdbcTemplate.update(
			"""
			INSERT INTO user_experience_awards (user_id, problem_id, experience, awarded_at)
			VALUES (?, ?, ?, CURRENT_TIMESTAMP)
			ON CONFLICT (user_id, problem_id) DO NOTHING
			""",
			userId,
			problemId,
			experience
		) == 1;
	}

	private boolean awardForTestDatabase(long userId, long problemId, int experience) {
		return jdbcTemplate.update(
			"""
			INSERT INTO user_experience_awards (user_id, problem_id, experience, awarded_at)
			SELECT ?, ?, ?, CURRENT_TIMESTAMP
			WHERE NOT EXISTS (
				SELECT 1
				FROM user_experience_awards
				WHERE user_id = ? AND problem_id = ?
			)
			""",
			userId,
			problemId,
			experience,
			userId,
			problemId
		) == 1;
	}

	@Override
	public long sumByUserId(long userId) {
		Long total = jdbcTemplate.queryForObject(
			"SELECT COALESCE(SUM(experience), 0) FROM user_experience_awards WHERE user_id = ?",
			Long.class,
			userId
		);
		return total == null ? 0 : total;
	}

	@Override
	public List<UserExperienceRankingCandidate> findTopUsers(int limit) {
		return jdbcTemplate.query(
			"""
			SELECT u.username, SUM(award.experience) AS total_experience
			FROM users u
			INNER JOIN user_experience_awards award ON award.user_id = u.id
			WHERE u.deleted_at IS NULL
			  AND u.role <> 'ADMIN'
			GROUP BY u.id, u.username
			ORDER BY total_experience DESC, u.id ASC
			LIMIT ?
			""",
			(rs, rowNum) -> new UserExperienceRankingCandidate(
				rs.getString("username"),
				rs.getLong("total_experience")
			),
			limit
		);
	}

	private boolean isPostgresql(DataSource dataSource) {
		try (Connection connection = dataSource.getConnection()) {
			return "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName());
		} catch (SQLException exception) {
			throw new IllegalStateException("Failed to detect database type", exception);
		}
	}
}
