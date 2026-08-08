package com.pairing.chat.infrastructure.directory;

import com.pairing.chat.application.port.out.ChatDirectoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 채팅에 필요한 타 도메인 값을 읽기 전용으로 조회한다(협상/계정 도메인이 프로필 ID 기준 포트를 주지 않아,
 * 협상의 project·이름 조회와 같은 방식으로 직접 조회).
 */
@Component
@RequiredArgsConstructor
public class ChatDirectoryAdapter implements ChatDirectoryPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<NegotiationParties> findPartiesByNegotiationId(Long negotiationId) {
        if (negotiationId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT cp.account_id   AS client_account_id,
                       fp.account_id   AS freelancer_account_id,
                       p.title         AS project_title,
                       cp.company_name AS client_name,
                       fa.name         AS freelancer_name
                FROM negotiation n
                JOIN freelancer_profile fp ON fp.id = n.freelancer_id
                JOIN project p             ON p.id = n.project_id
                JOIN client_profile cp     ON cp.id = p.client_id
                LEFT JOIN account fa       ON fa.id = fp.account_id
                WHERE n.id = ?
                """;
        try {
            NegotiationParties parties = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                    new NegotiationParties(
                            rs.getObject("client_account_id", Long.class),
                            rs.getObject("freelancer_account_id", Long.class),
                            rs.getString("project_title"),
                            rs.getString("client_name"),
                            rs.getString("freelancer_name")),
                    negotiationId);
            return Optional.ofNullable(parties);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findDisplayName(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(
                    jdbcTemplate.queryForObject("SELECT name FROM account WHERE id = ?", String.class, accountId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
