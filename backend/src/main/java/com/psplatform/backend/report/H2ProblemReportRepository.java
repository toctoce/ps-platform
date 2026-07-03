package com.psplatform.backend.report;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class H2ProblemReportRepository implements ProblemReportRepository {

	private final JdbcTemplate jdbcTemplate;

	private final RowMapper<ProblemReport> rowMapper = (rs, rowNum) -> {
		Timestamp reviewedAt = rs.getTimestamp("reviewed_at");
		return new ProblemReport(
			rs.getLong("id"),
			rs.getLong("problem_id"),
			rs.getString("problem_title"),
			rs.getLong("reporter_user_id"),
			rs.getString("reporter_username"),
			ProblemReportType.valueOf(rs.getString("type")),
			rs.getString("message"),
			ProblemReportStatus.valueOf(rs.getString("status")),
			rs.getTimestamp("created_at").toLocalDateTime(),
			reviewedAt == null ? null : reviewedAt.toLocalDateTime(),
			rs.getString("admin_note")
		);
	};

	public H2ProblemReportRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public ProblemReport save(long problemId, long reporterUserId, ProblemReportCreateRequest request) {
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(
				"""
				INSERT INTO problem_reports (
					problem_id,
					reporter_user_id,
					type,
					message,
					status
				)
				VALUES (?, ?, ?, ?, ?)
				""",
				new String[] {"id"}
			);
			ps.setLong(1, problemId);
			ps.setLong(2, reporterUserId);
			ps.setString(3, request.type().name());
			ps.setString(4, request.message().trim());
			ps.setString(5, ProblemReportStatus.PENDING.name());
			return ps;
		}, keyHolder);

		Number key = keyHolder.getKey();
		if (key == null) {
			throw new IllegalStateException("Problem report ID was not generated");
		}
		return findById(key.longValue())
			.orElseThrow(() -> new IllegalStateException("Saved problem report was not found"));
	}

	@Override
	public Optional<ProblemReport> findById(long id) {
		return jdbcTemplate.query(selectSql() + " WHERE pr.id = ?", rowMapper, id)
			.stream()
			.findFirst();
	}

	@Override
	public List<ProblemReport> findAll() {
		return jdbcTemplate.query(selectSql() + " ORDER BY pr.id DESC", rowMapper);
	}

	@Override
	public ProblemReport updateReview(long id, ProblemReportStatus status, String adminNote) {
		LocalDateTime reviewedAt = status == ProblemReportStatus.PENDING ? null : LocalDateTime.now();
		jdbcTemplate.update(
			"""
			UPDATE problem_reports
			SET status = ?,
			    admin_note = ?,
			    reviewed_at = ?
			WHERE id = ?
			""",
			status.name(),
			normalizeNote(adminNote),
			reviewedAt == null ? null : Timestamp.valueOf(reviewedAt),
			id
		);
		return findById(id).orElseThrow(() -> new ProblemReportNotFoundException(id));
	}

	@Override
	public void deleteAll() {
		jdbcTemplate.update("DELETE FROM problem_reports");
	}

	private String selectSql() {
		return """
			SELECT pr.id, pr.problem_id, p.title AS problem_title,
			       pr.reporter_user_id,
			       CASE WHEN u.deleted_at IS NOT NULL THEN '탈퇴한 사용자' ELSE u.username END AS reporter_username,
			       pr.type, pr.message, pr.status, pr.created_at, pr.reviewed_at, pr.admin_note
			FROM problem_reports pr
			JOIN problems p ON p.id = pr.problem_id
			JOIN users u ON u.id = pr.reporter_user_id
			""";
	}

	private String normalizeNote(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
