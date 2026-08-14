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

    /**
     * 스냅샷 동결 + 포지션 임베딩 + <b>회차 레코드까지만</b>. AI 호출은 하지 않는다.
     *
     * <p><b>후보 채우기(AI 호출)를 여기에 같이 두면 안 된다.</b> 한 트랜잭션이면 회차 행이 AI 호출이
     * 끝날 때까지 커밋되지 않는다. 그 수 초~수십 초 동안 클라이언트가 추천 후보 탭을 열면 회차가
     * 아예 없고, 호출이 실패하거나 컨테이너가 교체되면 회차 행이 <b>흔적도 없이 사라진다</b> —
     * 그러면 "무엇이 실패했는지"를 알 방법이 없다(2026-08-13).
     *
     * <p>재추천 경로({@code MatchingRerecommendService} → {@link RerecommendRequestedEventListener})는
     * 원래부터 이렇게 쪼개져 있었다. 최초 추천만 안 그랬다.
     *
     * @return 만든 회차 ID. 이미 회차가 있으면(멱등 스킵) null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    Long openInitialRound(Long projectId, Long positionId) {
        if (matchingRoundRepository.countByPositionId(positionId) > 0) {
            log.info("[모집 시작 - 멱등 스킵] 이미 회차가 있어 건너뜀. positionId={}", positionId);
            return null;
        }

        ProjectPositionSummary summary = projectDirectoryPort.findPositionSummary(projectId, positionId);
        freezeSnapshot(projectId, positionId, summary);
        matchingPort.upsertPositionEmbedding(positionId, PositionEmbeddingTextBuilder.buildText(summary));
        return matchingRoundCreationService
                .openRound(projectId, positionId, RecommendationType.INITIAL, summary.headcount(), 0L)
                .getId();
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
