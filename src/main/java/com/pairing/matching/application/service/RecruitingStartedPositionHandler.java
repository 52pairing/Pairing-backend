package com.pairing.matching.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.global.exception.BusinessException;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.exception.MatchingErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 포지션 1건의 "모집 시작" 처리(스냅샷 동결 -&gt; 임베딩 upsert -&gt; 최초 추천 라운드 생성).
 * {@link RecruitingStartedEventListener}가 프로젝트의 포지션마다 이 빈을 통해(자기 자신 호출이
 * 아니라 진짜 다른 빈 호출로) 부른다 — REQUIRES_NEW가 실제로 적용되려면 스프링 프록시를
 * 거쳐야 하기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class RecruitingStartedPositionHandler {

    private final ProjectDirectoryPort projectDirectoryPort;
    private final MatchingPort matchingPort;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingSnapshotRepository matchingSnapshotRepository;
    private final MatchingRoundCreationService matchingRoundCreationService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void startInitialRecommendation(Long projectId, Long positionId) {
        if (matchingRoundRepository.countByPositionId(positionId) > 0) {
            log.info("[모집 시작 - 멱등 스킵] 이미 회차가 있어 건너뜀. positionId={}", positionId);
            return;
        }

        ProjectPositionSummary summary = projectDirectoryPort.findPositionSummary(projectId, positionId);
        freezeSnapshot(projectId, positionId, summary);
        matchingPort.upsertPositionEmbedding(positionId, PositionEmbeddingTextBuilder.buildText(summary));
        matchingRoundCreationService.createRound(projectId, positionId, RecommendationType.INITIAL,
                summary.headcount(), 0L);
    }

    private void freezeSnapshot(Long projectId, Long positionId, ProjectPositionSummary summary) {
        Map<String, Object> projectPayload = new LinkedHashMap<>();
        projectPayload.put("title", summary.projectTitle());
        projectPayload.put("companyName", summary.companyName());
        projectPayload.put("workLabel", summary.workLabel());
        projectPayload.put("periodLabel", summary.periodLabel());
        projectPayload.put("startDesiredDate", summary.startDesiredDate());
        projectPayload.put("budgetAmount", summary.budgetAmount());
        // 매칭 요청 상세에서만 노출한다(3번 요청, 2026-08-09). 프로젝트 수정으로 바뀔 수 있는
        // 필드라 R32 대상 — companyProfile(라이브 유지)과는 반대로 여기서 얼려둔다.
        projectPayload.put("mainTask", summary.mainTask());
        saveSnapshotIfAbsent(projectId, positionId, SnapshotType.PROJECT, projectPayload);

        Map<String, Object> positionPayload = new LinkedHashMap<>();
        positionPayload.put("jobRole", summary.jobRole());
        positionPayload.put("requiredSkills", summary.requiredSkills());
        positionPayload.put("minCareerYears", summary.minCareerYears());
        positionPayload.put("headcount", summary.headcount());
        positionPayload.put("totalHeadcount", summary.totalHeadcount());
        saveSnapshotIfAbsent(projectId, positionId, SnapshotType.POSITION, positionPayload);
    }

    private void saveSnapshotIfAbsent(Long projectId, Long positionId, SnapshotType type,
                                      Map<String, Object> payload) {
        if (matchingSnapshotRepository.findByPositionIdAndSnapshotType(positionId, type).isPresent()) {
            return;
        }
        matchingSnapshotRepository.save(MatchingSnapshot.create(projectId, positionId, null, type,
                writeJson(payload)));
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BusinessException(MatchingErrorCode.INVALID_MATCHING_STATE);
        }
    }
}
