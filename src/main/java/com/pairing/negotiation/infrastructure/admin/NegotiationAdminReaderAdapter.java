package com.pairing.negotiation.infrastructure.admin;

import com.pairing.negotiation.application.port.out.NegotiationAdminReaderPort;
import com.pairing.negotiation.application.result.admin.AdminListItem;
import com.pairing.negotiation.application.result.admin.AdminRawLog;
import com.pairing.negotiation.application.result.admin.AdminSummary;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 협상 조회를 여러 도메인 테이블을 가로질러 읽기 전용으로 조회한다
 * (negotiation × project × client_profile × freelancer_profile × account, 그리고 ai_agent_log).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NegotiationAdminReaderAdapter implements NegotiationAdminReaderPort {

    private final JdbcTemplate jdbcTemplate;

    private NamedParameterJdbcTemplate named() {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public AdminSummary loadSummary() {
        AdminSummary counts = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) AS total,
                       SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress,
                       SUM(CASE WHEN status = 'AGREED' THEN 1 ELSE 0 END) AS agreed,
                       SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed,
                       COALESCE(AVG(total_round), 0) AS avg_round
                FROM negotiation
                """, (rs, n) -> new AdminSummary(
                rs.getLong("total"), rs.getLong("in_progress"), rs.getLong("agreed"),
                rs.getLong("failed"), rs.getDouble("avg_round"), 0));

        double avgDurationDays = averageDurationDays();
        return new AdminSummary(counts.total(), counts.inProgress(), counts.agreed(), counts.failed(),
                round1(counts.averageRound()), round1(avgDurationDays));
    }

    /** 종료된 협상의 평균 소요 일수. DB 이식성을 위해 날짜 차이는 애플리케이션에서 계산한다. */
    private double averageDurationDays() {
        List<Duration> durations = jdbcTemplate.query(
                "SELECT started_at, ended_at FROM negotiation WHERE ended_at IS NOT NULL",
                (rs, n) -> Duration.between(rs.getTimestamp("started_at").toLocalDateTime(),
                        rs.getTimestamp("ended_at").toLocalDateTime()));
        if (durations.isEmpty()) {
            return 0;
        }
        return durations.stream().mapToDouble(d -> d.toMinutes() / (60.0 * 24.0)).average().orElse(0);
    }

    @Override
    public Page<AdminListItem> search(String keyword, NegotiationStatus status, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : "%" + keyword.toLowerCase() + "%";
        String statusName = status == null ? null : status.name();

        String where = """
                FROM negotiation n
                JOIN project p            ON p.id = n.project_id
                JOIN client_profile cp    ON cp.id = p.client_id
                JOIN freelancer_profile fp ON fp.id = n.freelancer_id
                LEFT JOIN account a       ON a.id = fp.account_id
                WHERE (CAST(:status AS varchar) IS NULL OR n.status = :status)
                  AND (CAST(:kw AS varchar) IS NULL
                       OR LOWER(p.title) LIKE :kw
                       OR LOWER(cp.company_name) LIKE :kw
                       OR LOWER(a.name) LIKE :kw)
                """;
        // CAST 가 붙은 이유: 네이티브 SQL 에서 파라미터가 컬럼과 비교되지 않고 홀로
        // "? IS NULL" 로 놓이면 PostgreSQL 이 타입을 추론하지 못해 파싱 단계에서 터진다
        //   ERROR: could not determine data type of parameter $1
        // 값이 null 인지와 무관하게 항상 실패하므로, 검색 API 전체가 500 이었다.
        // 테스트 H2(MODE=PostgreSQL)는 이 구문을 통과시켜 초록불이었다 — 로컬만 보고 믿으면 안 된다.

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", statusName)
                .addValue("kw", kw);

        Long total = named().queryForObject("SELECT COUNT(*) " + where, params, Long.class);
        if (total == null || total == 0) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        String sql = "SELECT n.id, n.project_id, p.title, cp.company_name, a.name AS freelancer_name, "
                + "n.status, n.total_round, n.started_at, n.ended_at " + where
                + " ORDER BY n.started_at DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        List<AdminListItem> content = named().query(sql, params, LIST_ITEM_MAPPER);
        return new PageImpl<>(content, pageable, total);
    }

    private static final RowMapper<AdminListItem> LIST_ITEM_MAPPER = (rs, n) -> new AdminListItem(
            rs.getLong("id"),
            rs.getLong("project_id"),
            rs.getString("title"),
            rs.getString("company_name"),
            rs.getString("freelancer_name"),
            NegotiationStatus.valueOf(rs.getString("status")),
            rs.getInt("total_round"),
            toLdt(rs.getTimestamp("started_at")),
            toLdt(rs.getTimestamp("ended_at")));

    @Override
    public List<AdminRawLog> loadRawLogs(Long negotiationId) {
        try {
            return jdbcTemplate.query("""
                    SELECT id, agent_type, model, request_json, response_json,
                           prompt_tokens, output_tokens, latency_ms, error_message, created_at
                    FROM ai_agent_log
                    WHERE ref_type = 'NEGOTIATION' AND ref_id = ?
                    ORDER BY created_at, id
                    """, (rs, n) -> new AdminRawLog(
                    rs.getLong("id"),
                    rs.getString("agent_type"),
                    rs.getString("model"),
                    rs.getString("request_json"),
                    rs.getString("response_json"),
                    (Integer) rs.getObject("prompt_tokens"),
                    (Integer) rs.getObject("output_tokens"),
                    (Integer) rs.getObject("latency_ms"),
                    rs.getString("error_message"),
                    toLdt(rs.getTimestamp("created_at"))), negotiationId);
        } catch (DataAccessException e) {
            // ai_agent_log 는 AI 서비스(파이썬)가 기록한다. 아직 미기록이거나 환경에 테이블이 없으면 빈 리스트.
            log.debug("ai_agent_log 조회 불가(빈 결과 반환) negotiationId={}: {}", negotiationId, e.getMessage());
            return List.of();
        }
    }

    private static LocalDateTime toLdt(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
