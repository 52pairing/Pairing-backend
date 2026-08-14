package com.pairing.matching.application.service;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.matching.presentation.api.response.CandidateListResponse;
import com.pairing.matching.presentation.api.response.CandidateResponse;
import com.pairing.meta.domain.model.SkillCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * {@link CandidateListResponse}/{@link CandidateResponse} 조립. 매칭 조회·거절·재추천 서비스가 공유한다.
 *
 * <p>fitReason은 DB에 "|"로 이어붙인 하나의 문자열로 저장하고(스키마상 fit_reason은 단일 TEXT 컬럼),
 * 여기서 다시 나눠 태그 목록으로 돌려준다. Pairing-python의 LLM 응답 스키마(Stage E)가 이 구분자로
 * 합쳐서 내려주도록 3일차에 맞춘다.
 */
@Component
@RequiredArgsConstructor
class CandidateResponseAssembler {

    private static final int MAX_PAID_RERECOMMEND = 5;

    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final ProjectDirectoryPort projectDirectoryPort;

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
        List<CandidateResponse> candidates = exposedCandidates.stream()
                .map(this::toCandidateResponse)
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

    private CandidateResponse toCandidateResponse(MatchingCandidate candidate) {
        FreelancerCardSummary card = freelancerDirectoryPort.findCardSummary(candidate.getFreelancerId());
        FreelancerConditionResponse condition = freelancerDirectoryPort.findCondition(candidate.getFreelancerId());
        boolean requested = matchingRequestRepository.existsByCandidateId(candidate.getId());
        // 문구를 서버가 정한다. 프론트가 두 불리언을 조합해 문구를 만들면 우선순위(거절 > 요청)가
        // 서버의 isSelectable() 판정과 갈릴 수 있고, 그러면 "선택 가능"으로 보이는 카드가 눌렀을 때
        // 거부당한다.
        CandidateResponse.Status status = CandidateResponse.Status.of(requested, candidate.isRejected());

        List<SkillCode> skills = condition.skills().stream()
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
