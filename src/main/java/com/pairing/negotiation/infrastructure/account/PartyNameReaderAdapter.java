package com.pairing.negotiation.infrastructure.account;

import com.pairing.negotiation.application.port.out.PartyNameReaderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

    private Optional<String> queryForName(String sql, Long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, String.class, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
