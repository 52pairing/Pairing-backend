package com.pairing.negotiation.infrastructure.account;

import com.pairing.negotiation.application.port.out.PartyNameReaderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 표시용 이름을 account/client_profile 테이블에서 읽기 전용으로 조회한다.
 * 소유 도메인(account)이 프로필 ID 기준 조회 포트를 제공하지 않아, project 와 같은 방식으로
 * 협상이 직접 읽는다. 조회 전용이라 도메인 모델 없이 스칼라만 가져온다.
 */
@Component
@RequiredArgsConstructor
public class PartyNameReaderAdapter implements PartyNameReaderPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<String> findFreelancerName(Long freelancerProfileId) {
        if (freelancerProfileId == null) {
            return Optional.empty();
        }
        return queryForName(
                "SELECT a.name FROM freelancer_profile f JOIN account a ON a.id = f.account_id WHERE f.id = ?",
                freelancerProfileId);
    }

    @Override
    public Optional<String> findClientCompanyName(Long clientProfileId) {
        if (clientProfileId == null) {
            return Optional.empty();
        }
        return queryForName("SELECT company_name FROM client_profile WHERE id = ?", clientProfileId);
    }

    @Override
    public Map<Long, String> findFreelancerNames(Collection<Long> freelancerProfileIds) {
        return queryNamesByIds(
                "SELECT f.id AS id, a.name AS name FROM freelancer_profile f "
                        + "JOIN account a ON a.id = f.account_id WHERE f.id IN (%s)",
                freelancerProfileIds);
    }

    @Override
    public Map<Long, String> findClientCompanyNames(Collection<Long> clientProfileIds) {
        return queryNamesByIds(
                "SELECT id AS id, company_name AS name FROM client_profile WHERE id IN (%s)",
                clientProfileIds);
    }

    private Optional<String> queryForName(String sql, Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, String.class, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** id → 이름 맵. 카드마다 한 건씩 읽던 것을 IN 절 한 번으로 모은다. 없는 ID 는 맵에서 빠진다. */
    private Map<Long, String> queryNamesByIds(String sqlTemplate, Collection<Long> ids) {
        List<Long> distinct = ids == null ? List.of()
                : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        String placeholders = distinct.stream().map(id -> "?").collect(Collectors.joining(","));
        Map<Long, String> result = new HashMap<>();
        jdbcTemplate.query(String.format(sqlTemplate, placeholders), rs -> {
            result.put(rs.getLong("id"), rs.getString("name"));
        }, distinct.toArray());
        return result;
    }
}
