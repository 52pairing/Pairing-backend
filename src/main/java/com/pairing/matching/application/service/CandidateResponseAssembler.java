package com.pairing.matching.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.MatchingSnapshot;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.model.SnapshotType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.domain.repository.MatchingSnapshotRepository;
import com.pairing.matching.presentation.api.response.CandidateListResponse;
import com.pairing.matching.presentation.api.response.CandidateProfileSnapshotResponse;
import com.pairing.matching.presentation.api.response.CandidateResponse;
import com.pairing.meta.domain.model.SkillCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link CandidateListResponse}/{@link CandidateResponse} 조립. 매칭 조회·거절·재추천 서비스가 공유한다.
 *
 * <p>fitReason은 DB에 "|"로 이어붙인 하나의 문자열로 저장하고(스키마상 fit_reason은 단일 TEXT 컬럼),
 * 여기서 다시 나눠 태그 목록으로 돌려준다. Pairing-python의 LLM 응답 스키마(Stage E)가 이 구분자로
 * 합쳐서 내려주도록 3일차에 맞춘다.
 *
 * <p><b>카드에서 조건은 얼린 값, 평판은 지금 값이다</b>(2026-08-15). 직무·경력·스킬·단가는 노출 시점
 * 스냅샷에서 읽고, 이름·프로필 사진·등급·평점·리뷰수는 계속 라이브로 읽는다. 클라이언트는 카드를 보고
 * 후보를 고르므로 그 숫자가 프로필 상세·협상 출발점과 같아야 하고, 평판은 반대로 최신이 맞다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CandidateResponseAssembler {

    private static final int MAX_PAID_RERECOMMEND = 5;

    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final MatchingSnapshotRepository matchingSnapshotRepository;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final ObjectMapper objectMapper;

    CandidateListResponse build(MatchingRound round, Long accountId) {
        if (!projectDirectoryPort.isOwnedByAccount(round.getProjectId(), accountId)) {
            throw new BusinessException(GlobalErrorCode.ACCESS_DENIED);
        }

        // **회차가 아니라 포지션 전체**를 읽는다. 재추천은 새 회차를 만드는데 최신 회차만 보여주면
        // 이전 회차 후보가 화면에서 사라지고, R02 예외조건 5(이미 추천된 프리랜서는 다음 회차에서 제외)
        // 때문에 다시 나올 방법도 없다 - 유료 재추천으로 후보를 늘리려던 클라이언트가 오히려 잃는다.
        //
        // 인자로 받은 round 는 머리말(회차 번호·유형·노출 인원·재추천 가능 여부)에만 쓴다.
        List<MatchingCandidate> exposedCandidates = matchingCandidateRepository
                .findExposedByPositionId(round.getPositionId());
        boolean budgetWarned = exposedCandidates.stream()
                .anyMatch(CandidateResponseAssembler::hasGuardReason);
        Map<Long, FreelancerConditionResponse> frozenConditions = loadFrozenConditions(round.getPositionId());
        List<CandidateResponse> candidates = exposedCandidates.stream()
                .map(candidate -> toCandidateResponse(candidate, frozenConditions))
                .toList();

        long paidUsed = matchingRoundRepository.countByProjectIdAndRoundType(round.getProjectId(),
                RecommendationType.PAID);
        long freeUsed = matchingRoundRepository.countByProjectIdAndRoundType(round.getProjectId(),
                RecommendationType.FREE);
        boolean freeAvailable = freeUsed == 0
                && matchingRequestRepository.existsByProjectId(round.getProjectId())
                && !matchingRequestRepository.existsActiveByProjectId(round.getProjectId());
        int paidRemaining = (int) Math.max(0, MAX_PAID_RERECOMMEND - paidUsed);

        return new CandidateListResponse(round.getPositionId(), round.getId(), round.getRoundNo(),
                round.getRoundType(), round.getExposeCount(), freeAvailable, paidRemaining,
                round.isLowScoreWarned(), budgetWarned, candidates,
                // 회차가 생기자마자 커밋되므로(2026-08-13), 회차가 있다고 후보가 있는 건 아니다.
                // 상태를 안 보면 아직 채우는 중인 회차가 "후보 0명"으로 보인다.
                round.getStatus() == MatchingRoundStatus.RUNNING,
                round.getStatus() == MatchingRoundStatus.FAILED);
    }

    /**
     * 회차가 아직 없을 때의 응답. 회차에서 읽을 게 없으니 포지션 정보를 직접 받는다.
     *
     * <p><b>남은 유료 재추천은 여기서도 실제로 센다.</b> 한도가 프로젝트 단위라 이 포지션에 회차가
     * 없어도 같은 프로젝트의 다른 포지션이 이미 썼을 수 있다 — 상한을 그대로 내보내면 거짓말이 된다.
     */
    CandidateListResponse buildPreparing(Long positionId, Long projectId, int headcount) {
        long paidUsed = matchingRoundRepository.countByProjectIdAndRoundType(projectId, RecommendationType.PAID);
        return CandidateListResponse.preparing(positionId, headcount,
                (int) Math.max(0, MAX_PAID_RERECOMMEND - paidUsed));
    }

    /**
     * 한 포지션의 프리랜서 스냅샷을 <b>한 번에</b> 읽어 프리랜서별 조건으로 만든다.
     *
     * <p>스냅샷은 (프리랜서, 포지션)마다 1건이라, 같은 사람이 단가를 올린 뒤 다른 포지션에 추천되면
     * 그 포지션에는 <b>올린 값</b>이 얼린다. 먼저 추천된 포지션은 계속 예전 값을 본다.
     *
     * <p>조건을 못 읽는 스냅샷은 지도에 넣지 않는다 — 캡처가 부분 실패한 건이라(이력서가 없으면
     * 조건도 같이 비는 경우가 있다) 그대로 쓰면 카드가 빈칸이 된다. 부르는 쪽에서 현재 값으로 넘어간다.
     */
    private Map<Long, FreelancerConditionResponse> loadFrozenConditions(Long positionId) {
        Map<Long, FreelancerConditionResponse> conditions = new HashMap<>();
        for (MatchingSnapshot snapshot : matchingSnapshotRepository
                .findAllByPositionIdAndSnapshotType(positionId, SnapshotType.FREELANCER)) {
            readCondition(snapshot).ifPresent(condition -> conditions.put(snapshot.getFreelancerId(), condition));
        }
        return conditions;
    }

    private Optional<FreelancerConditionResponse> readCondition(MatchingSnapshot snapshot) {
        try {
            return Optional.ofNullable(objectMapper.readValue(snapshot.getSnapshotJson(),
                    CandidateProfileSnapshotResponse.SnapshotPayload.class).condition());
        } catch (JsonProcessingException e) {
            log.warn("MATCHING_DEBUG java.candidate.card.snapshot_unreadable freelancerId={} positionId={} cause={}",
                    snapshot.getFreelancerId(), snapshot.getPositionId(), e.getMessage());
            return Optional.empty();
        }
    }

    private CandidateResponse toCandidateResponse(MatchingCandidate candidate,
                                                  Map<Long, FreelancerConditionResponse> frozenConditions) {
        // **평판은 라이브다.** 이름·프로필 사진·등급·평점·리뷰수는 추천된 뒤에 쌓인 것도 반영되는 게
        // 맞다 — 클라이언트가 사람을 고르는 데 쓰는 최신 정보다.
        FreelancerCardSummary card = freelancerDirectoryPort.findCardSummary(candidate.getFreelancerId());
        // **조건은 얼린 값이다.** 직무·경력·스킬·단가는 노출 시점 값을 쓴다. 라이브로 읽으면 프리랜서가
        // 단가를 올렸을 때 카드는 새 값, 프로필 상세와 협상 시작가는 얼린 값이 되어 화면끼리 숫자가
        // 어긋난다. 클라이언트는 카드를 보고 후보를 고르므로 그 값이 협상 출발점과 같아야 한다.
        FreelancerConditionResponse condition = frozenConditions.computeIfAbsent(candidate.getFreelancerId(),
                freelancerId -> {
                    // 이 코드 배포 전에 노출된 후보는 스냅샷이 없다. 없다고 카드를 못 그리면 안 된다.
                    log.info("MATCHING_DEBUG java.candidate.card.condition source=LIVE freelancerId={} reason=snapshot_absent",
                            freelancerId);
                    return freelancerDirectoryPort.findCondition(freelancerId);
                });
        boolean requested = matchingRequestRepository.existsByCandidateId(candidate.getId());
        // 문구를 서버가 정한다. 프론트가 두 불리언을 조합해 문구를 만들면 우선순위(거절 > 요청)가
        // 서버의 isSelectable() 판정과 갈릴 수 있고, 그러면 "선택 가능"으로 보이는 카드가 눌렀을 때
        // 거부당한다.
        CandidateResponse.Status status = CandidateResponse.Status.of(requested, candidate.isRejected());

        // 스냅샷에서 읽은 조건은 스킬이 비어 있을 수 있다. 캡처가 부분 실패했거나 이 코드 이전에
        // 손으로 심긴 자료가 그렇다 — 카드 하나 때문에 목록 전체가 500이 되면 안 된다.
        List<SkillCode> skills = condition.skills() == null ? List.of()
                : condition.skills().stream()
                        .map(FreelancerConditionResponse.Skill::skillCode)
                        .toList();

        return new CandidateResponse(
                candidate.getId(),
                candidate.getFreelancerId(),
                card.name(),
                card.profileImageUrl(),
                condition.jobRole(),
                condition.careerYears(),
                card.grade().name(),
                card.ratingAverage(),
                card.reviewCount(),
                skills,
                splitFitReasons(candidate.getFitReason()),
                condition.payUnit(),
                condition.payAmount(),
                candidate.getRankNo() != null ? candidate.getRankNo() : 0,
                requested,
                candidate.isRejected(),
                status,
                status.getLabel()
        );
    }

    static List<String> splitFitReasons(String fitReason) {
        if (fitReason == null || fitReason.isBlank()) {
            return List.of();
        }
        return Arrays.stream(fitReason.split("\\|"))
                .map(String::trim)
                .filter(reason -> !reason.isEmpty())
                .toList();
    }

    private static boolean hasGuardReason(MatchingCandidate candidate) {
        return candidate.getGuardReason() != null && !candidate.getGuardReason().isBlank();
    }
}
