package com.pairing.matching.infrastructure.admin;

import com.pairing.matching.application.port.out.MatchingAdminReaderPort;
import com.pairing.matching.application.result.admin.AiLogItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingItem;
import com.pairing.matching.application.result.admin.EmbeddingMissingResult;
import com.pairing.matching.application.result.admin.EmbeddingMissingSummary;
import com.pairing.matching.application.result.admin.MatchingDiagnostics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchingAdminReaderAdapter implements MatchingAdminReaderPort {

    private static final String FREELANCER = "FREELANCER";
    private static final String POSITION = "POSITION";

    private final JdbcTemplate jdbcTemplate;

    private NamedParameterJdbcTemplate named() {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public EmbeddingMissingResult findMissingEmbeddings(String targetType, Pageable pageable) {
        String normalizedType = normalizeTargetType(targetType);
        EmbeddingMissingSummary summary = new EmbeddingMissingSummary(
                countMissingFreelancers(), countMissingPositions());

        if (FREELANCER.equals(normalizedType)) {
            return new EmbeddingMissingResult(summary, missingFreelancers(pageable));
        }
        if (POSITION.equals(normalizedType)) {
            return new EmbeddingMissingResult(summary, missingPositions(pageable));
        }
        return new EmbeddingMissingResult(summary, missingAll(pageable));
    }

    private long countMissingFreelancers() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM freelancer_profile fp
                JOIN account a ON a.id = fp.account_id
                JOIN resume r ON r.account_id = fp.account_id
                LEFT JOIN freelancer_embedding fe ON fe.freelancer_id = fp.id
                WHERE a.status = 'ACTIVE'
                  AND fp.ai_matching_agreed IS TRUE
                  AND fp.matching_paused IS FALSE
                  AND fe.freelancer_id IS NULL
                """, Long.class);
        return count == null ? 0 : count;
    }

    private long countMissingPositions() {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT pp.id)
                FROM project_position pp
                JOIN project p ON p.id = pp.project_id
                LEFT JOIN position_embedding pe ON pe.position_id = pp.id
                WHERE p.status = 'RECRUITING'
                  AND pp.status = 'RECRUITING'
                  AND pe.position_id IS NULL
                """, Long.class);
        return count == null ? 0 : count;
    }

    private Page<EmbeddingMissingItem> missingFreelancers(Pageable pageable) {
        String from = """
                FROM freelancer_profile fp
                JOIN account a ON a.id = fp.account_id
                JOIN resume r ON r.account_id = fp.account_id
                LEFT JOIN freelancer_embedding fe ON fe.freelancer_id = fp.id
                LEFT JOIN LATERAL (
                    SELECT l.status, l.created_at
                    FROM ai_agent_log l
                    WHERE l.ref_type = 'FREELANCER' AND l.ref_id = fp.id
                    ORDER BY l.created_at DESC, l.id DESC
                    LIMIT 1
                ) last_log ON TRUE
                WHERE a.status = 'ACTIVE'
                  AND fp.ai_matching_agreed IS TRUE
                  AND fp.matching_paused IS FALSE
                  AND fe.freelancer_id IS NULL
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + from, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<EmbeddingMissingItem> content = jdbcTemplate.query("""
                SELECT 'FREELANCER' AS target_type, fp.id AS target_id, a.name AS display_name,
                       a.status AS status, 'EMBEDDING_NOT_FOUND' AS reason, fe.model,
                       last_log.status AS last_log_status, last_log.created_at AS last_log_at
                """ + from + " ORDER BY fp.id LIMIT ? OFFSET ?",
                this::mapMissingItem, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, total);
    }

    private Page<EmbeddingMissingItem> missingPositions(Pageable pageable) {
        String from = """
                FROM project_position pp
                JOIN project p ON p.id = pp.project_id
                LEFT JOIN position_embedding pe ON pe.position_id = pp.id
                LEFT JOIN LATERAL (
                    SELECT l.status, l.created_at
                    FROM ai_agent_log l
                    WHERE l.ref_type = 'POSITION' AND l.ref_id = pp.id
                    ORDER BY l.created_at DESC, l.id DESC
                    LIMIT 1
                ) last_log ON TRUE
                WHERE p.status = 'RECRUITING'
                  AND pp.status = 'RECRUITING'
                  AND pe.position_id IS NULL
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (SELECT DISTINCT pp.id " + from + ") missing",
                Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<EmbeddingMissingItem> content = jdbcTemplate.query("""
                SELECT 'POSITION' AS target_type, pp.id AS target_id,
                       p.title || ' / ' || pp.job_role AS display_name,
                       pp.status AS status, 'EMBEDDING_NOT_FOUND' AS reason, pe.model,
                       last_log.status AS last_log_status, last_log.created_at AS last_log_at
                """ + from + " ORDER BY pp.id LIMIT ? OFFSET ?",
                this::mapMissingItem, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, total);
    }

    private Page<EmbeddingMissingItem> missingAll(Pageable pageable) {
        String sql = """
                SELECT *
                FROM (
                    SELECT 'FREELANCER' AS target_type, fp.id AS target_id, a.name AS display_name,
                           a.status AS status, 'EMBEDDING_NOT_FOUND' AS reason, fe.model,
                           last_log.status AS last_log_status, last_log.created_at AS last_log_at
                    FROM freelancer_profile fp
                    JOIN account a ON a.id = fp.account_id
                    JOIN resume r ON r.account_id = fp.account_id
                    LEFT JOIN freelancer_embedding fe ON fe.freelancer_id = fp.id
                    LEFT JOIN LATERAL (
                        SELECT l.status, l.created_at
                        FROM ai_agent_log l
                        WHERE l.ref_type = 'FREELANCER' AND l.ref_id = fp.id
                        ORDER BY l.created_at DESC, l.id DESC
                        LIMIT 1
                    ) last_log ON TRUE
                    WHERE a.status = 'ACTIVE'
                      AND fp.ai_matching_agreed IS TRUE
                      AND fp.matching_paused IS FALSE
                      AND fe.freelancer_id IS NULL
                    UNION ALL
                    SELECT 'POSITION' AS target_type, pp.id AS target_id,
                           p.title || ' / ' || pp.job_role AS display_name,
                           pp.status AS status, 'EMBEDDING_NOT_FOUND' AS reason, pe.model,
                           last_log.status AS last_log_status, last_log.created_at AS last_log_at
                    FROM project_position pp
                    JOIN project p ON p.id = pp.project_id
                    LEFT JOIN position_embedding pe ON pe.position_id = pp.id
                    LEFT JOIN LATERAL (
                        SELECT l.status, l.created_at
                        FROM ai_agent_log l
                        WHERE l.ref_type = 'POSITION' AND l.ref_id = pp.id
                        ORDER BY l.created_at DESC, l.id DESC
                        LIMIT 1
                    ) last_log ON TRUE
                    WHERE p.status = 'RECRUITING'
                      AND pp.status = 'RECRUITING'
                      AND pe.position_id IS NULL
                ) missing
                """;
        long total = countMissingFreelancers() + countMissingPositions();
        if (total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        List<EmbeddingMissingItem> content = jdbcTemplate.query(sql + " ORDER BY target_type, target_id LIMIT ? OFFSET ?",
                this::mapMissingItem, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(content, pageable, total);
    }

    private EmbeddingMissingItem mapMissingItem(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new EmbeddingMissingItem(
                rs.getString("target_type"),
                rs.getLong("target_id"),
                rs.getString("display_name"),
                rs.getString("status"),
                rs.getString("reason"),
                rs.getString("model"),
                rs.getString("last_log_status"),
                toLdt(rs.getTimestamp("last_log_at"))
        );
    }

    @Override
    public Page<AiLogItem> findAiLogs(String agentType, String refType, Long refId, String status,
                                      LocalDateTime from, LocalDateTime to, Pageable pageable) {
        try {
            String where = """
                    FROM ai_agent_log
                    WHERE (:agentType IS NULL OR agent_type = :agentType)
                      AND (:refType IS NULL OR ref_type = :refType)
                      AND (:refId IS NULL OR ref_id = :refId)
                      AND (:status IS NULL OR status = :status)
                      AND (:from IS NULL OR created_at >= :from)
                      AND (:to IS NULL OR created_at <= :to)
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("agentType", blankToNull(agentType))
                    .addValue("refType", blankToNull(refType))
                    .addValue("refId", refId)
                    .addValue("status", blankToNull(status))
                    .addValue("from", from)
                    .addValue("to", to);

            Long total = named().queryForObject("SELECT COUNT(*) " + where, params, Long.class);
            if (total == null || total == 0) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            params.addValue("limit", pageable.getPageSize())
                    .addValue("offset", pageable.getOffset());

            List<AiLogItem> content = named().query("""
                    SELECT id, agent_type, ref_type, ref_id, status, error_message, created_at
                    """ + where + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset",
                    params,
                    (rs, n) -> new AiLogItem(
                            rs.getLong("id"),
                            rs.getString("agent_type"),
                            rs.getString("ref_type"),
                            (Long) rs.getObject("ref_id"),
                            rs.getString("status"),
                            rs.getString("error_message"),
                            toLdt(rs.getTimestamp("created_at"))
                    ));
            return new PageImpl<>(content, pageable, total);
        } catch (DataAccessException e) {
            log.debug("ai_agent_log 조회 불가(빈 결과 반환): {}", e.getMessage());
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    @Override
    public MatchingDiagnostics findDiagnostics(Long projectId, Long positionId) {
        MatchingDiagnostics.ProjectInfo project = findProjectInfo(projectId);
        MatchingDiagnostics.PositionInfo position = findPositionInfo(projectId, positionId);
        MatchingDiagnostics.SnapshotInfo snapshots = new MatchingDiagnostics.SnapshotInfo(
                exists("SELECT COUNT(*) FROM matching_snapshot WHERE project_id = ? AND snapshot_type = 'PROJECT'",
                        projectId),
                exists("SELECT COUNT(*) FROM matching_snapshot WHERE position_id = ? AND snapshot_type = 'POSITION'",
                        positionId)
        );
        MatchingDiagnostics.EmbeddingInfo embeddings = findEmbeddingInfo(positionId);
        MatchingDiagnostics.RoundInfo round = findLatestRound(projectId, positionId);
        MatchingDiagnostics.CountInfo counts = new MatchingDiagnostics.CountInfo(
                count("SELECT COUNT(*) FROM matching_candidate WHERE position_id = ?", positionId),
                count("SELECT COUNT(*) FROM matching_candidate WHERE position_id = ? AND is_exposed IS TRUE", positionId),
                count("SELECT COUNT(*) FROM matching_request WHERE project_id = ? AND position_id = ?",
                        projectId, positionId)
        );
        return new MatchingDiagnostics(project, position, snapshots, embeddings, round, counts, findLastAiLog(positionId));
    }

    private MatchingDiagnostics.ProjectInfo findProjectInfo(Long projectId) {
        List<MatchingDiagnostics.ProjectInfo> rows = jdbcTemplate.query("""
                SELECT id, title, status, payment_status
                FROM project
                WHERE id = ?
                """, (rs, n) -> new MatchingDiagnostics.ProjectInfo(
                rs.getLong("id"), rs.getString("title"), rs.getString("status"), rs.getString("payment_status")),
                projectId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MatchingDiagnostics.PositionInfo findPositionInfo(Long projectId, Long positionId) {
        List<MatchingDiagnostics.PositionInfo> rows = jdbcTemplate.query("""
                SELECT id, status, job_category, job_role
                FROM project_position
                WHERE project_id = ? AND id = ?
                """, (rs, n) -> new MatchingDiagnostics.PositionInfo(
                rs.getLong("id"), rs.getString("status"), rs.getString("job_category"), rs.getString("job_role")),
                projectId, positionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MatchingDiagnostics.EmbeddingInfo findEmbeddingInfo(Long positionId) {
        List<String> models = jdbcTemplate.query("SELECT model FROM position_embedding WHERE position_id = ?",
                (rs, n) -> rs.getString("model"), positionId);
        Long freelancerEmbeddingCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT fe.freelancer_id)
                FROM freelancer_embedding fe
                JOIN freelancer_profile fp ON fp.id = fe.freelancer_id
                JOIN account a ON a.id = fp.account_id
                JOIN freelancer_condition fc ON fc.account_id = fp.account_id
                JOIN project_position pp ON pp.id = ?
                JOIN condition_skill cs ON cs.condition_id = fc.id
                JOIN position_skill ps ON ps.position_id = pp.id AND ps.skill_code = cs.skill_code
                WHERE a.status = 'ACTIVE'
                  AND fp.ai_matching_agreed IS TRUE
                  AND fp.matching_paused IS FALSE
                  AND fc.job_category = pp.job_category
                  AND fc.job_role = pp.job_role
                """, Long.class, positionId);
        return new MatchingDiagnostics.EmbeddingInfo(!models.isEmpty(),
                models.isEmpty() ? null : models.get(0),
                freelancerEmbeddingCount == null ? 0 : freelancerEmbeddingCount);
    }

    private MatchingDiagnostics.RoundInfo findLatestRound(Long projectId, Long positionId) {
        List<MatchingDiagnostics.RoundInfo> rows = jdbcTemplate.query("""
                SELECT id, round_no, round_type, status
                FROM matching_round
                WHERE project_id = ? AND position_id = ?
                ORDER BY round_no DESC, id DESC
                LIMIT 1
                """, (rs, n) -> new MatchingDiagnostics.RoundInfo(
                rs.getLong("id"), rs.getInt("round_no"), rs.getString("round_type"), rs.getString("status")),
                projectId, positionId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private MatchingDiagnostics.LastAiLogInfo findLastAiLog(Long positionId) {
        try {
            List<MatchingDiagnostics.LastAiLogInfo> rows = jdbcTemplate.query("""
                    SELECT status, created_at, error_message
                    FROM ai_agent_log
                    WHERE ref_type = 'POSITION' AND ref_id = ?
                    ORDER BY created_at DESC, id DESC
                    LIMIT 1
                    """, (rs, n) -> new MatchingDiagnostics.LastAiLogInfo(
                    rs.getString("status"),
                    toLdt(rs.getTimestamp("created_at")),
                    rs.getString("error_message")
            ), positionId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException e) {
            return null;
        }
    }

    private boolean exists(String sql, Object... args) {
        return count(sql, args) > 0;
    }

    private long count(String sql, Object... args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args);
        return count == null ? 0 : count;
    }

    private static String normalizeTargetType(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return "ALL";
        }
        return targetType.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static LocalDateTime toLdt(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
