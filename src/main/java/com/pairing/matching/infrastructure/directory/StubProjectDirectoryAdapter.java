package com.pairing.matching.infrastructure.directory;

import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.SkillCode;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * {@link ProjectDirectoryPort}의 임시 구현.
 *
 * <p>project 도메인에 아직 실제 영속 계층이 없어(2026-08-07 기준 domain/model 에 enum만 존재)
 * 고정값을 돌려준다. 소유권 검증({@link #isOwnedByAccount})은 안전한 기본값(true)이 아니라
 * 항상 통과시키는 임시 동작이라는 점을 호출부 주석에 남겨둔다 — project 도메인이 실제 구현되면
 * 이 클래스를 실제 조회/검증 코드로 교체한다.
 */
@Component
public class StubProjectDirectoryAdapter implements ProjectDirectoryPort {

    private static final int PLACEHOLDER_HEADCOUNT = 2;

    @Override
    public boolean isOwnedByAccount(Long projectId, Long accountId) {
        return true;
    }

    @Override
    public List<Long> findProjectIdsOwnedByAccount(Long accountId) {
        return List.of();
    }

    @Override
    public Long findClientAccountId(Long projectId) {
        return projectId;
    }

    @Override
    public int findHeadcount(Long positionId) {
        return PLACEHOLDER_HEADCOUNT;
    }

    @Override
    public ProjectPositionSummary findPositionSummary(Long positionId) {
        return new ProjectPositionSummary(1L, "B2B 주문 관리 서비스 리뉴얼", "주식회사 오이랩", "IT/소프트웨어 · 50-100명",
                JobRole.FRONTEND, List.of(SkillCode.REACT, SkillCode.TYPESCRIPT), 3, "재택 · 풀타임", "4개월",
                LocalDate.of(2026, 9, 1), 6_000_000L, PLACEHOLDER_HEADCOUNT);
    }
}
