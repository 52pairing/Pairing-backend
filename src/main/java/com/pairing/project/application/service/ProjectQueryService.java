package com.pairing.project.application.service;

import com.pairing.meta.domain.model.JobRole;
import com.pairing.meta.domain.model.PeriodUnit;
import com.pairing.meta.domain.model.SkillCode;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.project.application.result.ProjectPositionSummary;
import com.pairing.project.application.usecase.ProjectQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 스켈레톤이라 고정 응답을 돌려준다. 구현하면서 TODO 를 채운다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProjectQueryService implements ProjectQueryUseCase {

    @Override
    public boolean isOwnedBy(Long projectId, Long accountId) {
        // TODO: accountId -> clientProfileId 변환 후 project.client_id 와 비교
        return true;
    }

    @Override
    public List<Long> findProjectIdsByAccountId(Long accountId) {
        // TODO: accountId -> clientProfileId 변환 후 client_id 로 조회
        return List.of(1L);
    }

    @Override
    public Long findClientProfileId(Long projectId) {
        // TODO: project.client_id 반환. 없으면 예외
        return 1L;
    }

    @Override
    public int findHeadcount(Long positionId) {
        // TODO: project_position.headcount 반환. 없으면 예외
        return 2;
    }

    @Override
    public ProjectPositionSummary findProjectPositionSummary(Long projectId, Long positionId) {
        // TODO: project + project_position + position_skill 조인 조회
        return new ProjectPositionSummary(
                projectId, "페어링 웹 리뉴얼",
                JobRole.BACKEND, List.of(SkillCode.JAVA, SkillCode.SPRING_BOOT), 3, 2,
                WorkStyle.REMOTE, WorkForm.FULL_TIME, 6, PeriodUnit.MONTH,
                LocalDate.of(2026, 9, 1), 50_000_000L);
    }
}