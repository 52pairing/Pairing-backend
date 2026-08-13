package com.pairing.negotiation.infrastructure.account;

import com.pairing.negotiation.application.port.out.FreelancerConditionReaderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 프리랜서 희망 조건을 읽기 전용으로 조회한다. 소유 도메인(freelancer)이 프로필 ID 기준 조회 포트를
 * 주지 않아, {@code PartyNameReaderAdapter} 와 같은 방식으로 협상이 직접 읽는다.
 *
 * <p><b>조인 키에 주의.</b> {@code freelancer_condition} 의 키는 {@code account_id} 다
 * (스키마 문서의 {@code freelancer_id} 는 실물과 다르다). 협상은 프리랜서를
 * {@code freelancer_profile.id} 로 아므로 프로필을 거쳐 계정으로 번역한다.
 */
@Component
@RequiredArgsConstructor
public class FreelancerConditionReaderAdapter implements FreelancerConditionReaderPort {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<Long> findMinAcceptAmount(Long freelancerProfileId) {
        if (freelancerProfileId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT fc.min_accept_amount
                FROM freelancer_profile fp
                JOIN freelancer_condition fc ON fc.account_id = fp.account_id
                WHERE fp.id = ?
                """;
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, Long.class, freelancerProfileId));
        } catch (EmptyResultDataAccessException e) {
            // 희망 조건을 등록하지 않은 계정(구 데이터). 가드할 기준이 없으니 검증을 건너뛴다.
            return Optional.empty();
        }
    }
}
