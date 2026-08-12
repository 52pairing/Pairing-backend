package com.pairing.matching.application.service;

import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.global.exception.BusinessException;
import com.pairing.global.exception.GlobalErrorCode;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.FreelancerCardSummary;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
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

        List<MatchingCandidate> exposedCandidates = matchingCandidateRepository
                .findByRoundIdAndExposedTrueOrderByRankNo(round.getId());
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
                round.isLowScoreWarned(), budgetWarned, candidates);
    }

    private CandidateResponse toCandidateResponse(MatchingCandidate candidate) {
        FreelancerCardSummary card = freelancerDirectoryPort.findCardSummary(candidate.getFreelancerId());
        FreelancerConditionResponse condition = freelancerDirectoryPort.findCondition(candidate.getFreelancerId());
        boolean requested = matchingRequestRepository.existsByCandidateId(candidate.getId());

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
                candidate.isRejected()
        );
    }

    private static List<String> splitFitReasons(String fitReason) {
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
