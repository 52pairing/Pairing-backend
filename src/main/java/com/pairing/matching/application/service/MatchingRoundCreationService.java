package com.pairing.matching.application.service;

import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.freelancer.presentation.api.response.FreelancerConditionResponse;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.MatchingRecommendation;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.result.RankedFreelancer;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import com.pairing.meta.domain.model.SkillCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회차(라운드) 생성: Stage C~E(AI 서버 호출) -&gt; Stage F(가드) -&gt; 저장까지 한 번에 처리한다.
 *
 * <p>재추천(2일차)과 최초 추천(결제 완료 트리거, 3일차 예정) 둘 다 이 서비스를 공유한다.
 *
 * <p><b>알아둘 단순화(2026-08-07)</b>: similarity는 {@code MatchingPort.recommend()}가
 * Pairing-python 내부에서 Stage C~E를 한 번에 처리해 최종 순위만 돌려주므로, Spring이 별도로 받는
 * 유사도 값이 없다. 0.0을 임시로 채운다(참고용 필드라 랭킹/응답에는 안 쓰인다).
 *
 * <p><b>Stage F 가드(2026-08-09 구현)</b>: R02.3 요구사항 그대로 직무·스킬만 재검증한다("가드 AI로
 * 마지막 검증 (직무, 스킬 검증)"). 예산은 가드 대상이 아니다 — budgetCap은 협상 단계
 * ({@code NegotiationConditionCalculator})에서 이미 별도로 재검증되고, 요구사항에 "예산 조합"을
 * 가드에 넣으라는 근거가 없어서(이전에 STATE.md에 적혀있던 문구는 우리 자체 추정이었음, Stage B
 * 조건필터 폐기와 같은 사유) 넣지 않는다. 가드에 떨어진 후보는 노출되지 않고 다음 순위 후보가
 * 노출 인원 자리를 채운다.
 *
 * <p>이전에 노출됐던 프리랜서(R02 예외조건 5, 프로젝트 전체 기준) 제외는 Pairing-python이 벡터 검색
 * 전에 미리 걸러준다(2026-08-09) — 여기서는 그 목록을 조회해서 넘기기만 한다.
 */
@Component
@RequiredArgsConstructor
class MatchingRoundCreationService {

    private static final int POOL_MULTIPLIER = 3;
    private static final double LOW_SCORE_THRESHOLD = 50.0;

    private final MatchingPort matchingPort;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingCandidateRepository matchingCandidateRepository;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final ClientGradeResolver clientGradeResolver;
    private final BudgetCapCalculator budgetCapCalculator;

    /** 회차 생성 + 후보 채우기를 한 번에. 이미 비동기 문맥에서 도는 최초 추천(모집 시작)이 쓴다. */
    MatchingRound createRound(Long projectId, Long positionId, RecommendationType roundType, int recruitCount,
                              long costAmount) {
        MatchingRound round = openRound(projectId, positionId, roundType, recruitCount, costAmount);
        return fillCandidates(round);
    }

    /**
     * 회차 레코드만 만든다(LLM 호출 없음).
     *
     * <p>재추천은 이 단계까지만 **동기로** 처리한다. 회차가 저장돼야 무료/유료 한도 검증
     * ({@code MatchingRerecommendService.assertFreeAvailable})이 다음 요청을 막을 수 있어서다 —
     * LLM까지 기다렸다 저장하면 그 사이 같은 버튼을 두 번 누르면 회차가 두 개 생긴다.
     */
    MatchingRound openRound(Long projectId, Long positionId, RecommendationType roundType, int recruitCount,
                            long costAmount) {
        int roundNo = (int) matchingRoundRepository.countByPositionId(positionId) + 1;
        int poolSize = recruitCount * POOL_MULTIPLIER;
        Integer requestedCount = roundType == RecommendationType.PAID ? recruitCount : null;

        MatchingRound round = MatchingRound.create(projectId, positionId, roundNo, roundType, requestedCount,
                costAmount, recruitCount, poolSize);
        return matchingRoundRepository.save(round);
    }

    /**
     * 회차에 실제 후보를 채운다. **여기서 AI 서버(임베딩 검색 + LLM 재랭킹)를 부른다** — 수 초에서
     * 수십 초가 걸리므로 호출하는 쪽은 비동기 문맥이어야 한다.
     */
    MatchingRound fillCandidates(MatchingRound round) {
        Long projectId = round.getProjectId();
        Long positionId = round.getPositionId();
        int recruitCount = round.getExposeCount();

        // 포지션 조회가 추천 호출보다 앞이어야 한다 — budgetCap을 같이 넘겨야 해서다.
        ProjectPositionSummary position = projectDirectoryPort.findPositionSummary(projectId, positionId);
        long budgetCap = budgetCapCalculator.calculate(projectId, position.budgetAmount(),
                position.totalHeadcount(), position.periodValue(), position.periodUnit());

        List<Long> excludedFreelancerIds = matchingCandidateRepository.findFreelancerIdsByProjectId(projectId);
        MatchingRecommendation recommendation =
                matchingPort.recommend(positionId, recruitCount, POOL_MULTIPLIER, excludedFreelancerIds, budgetCap);

        if (recommendation.candidates().isEmpty()) {
            round.exhaust();
            return matchingRoundRepository.save(round);
        }

        List<RankedFreelancer> ranked = breakScoreTiesByGrade(recommendation.candidates());

        double gradeWeightPercent = clientGradeResolver.resolveMatchingWeightPercent(projectId);
        boolean lowScoreWarned =
                persistCandidates(round, positionId, recruitCount, ranked, gradeWeightPercent, position);

        if (lowScoreWarned) {
            round.warnLowScore();
        }
        round.complete();
        return matchingRoundRepository.save(round);
    }

    /**
     * LLM은 이미 base_score 내림차순으로 순위를 매겨서 돌려주지만, 동점일 때 어느 쪽을 앞에
     * 둘지는 정해주지 않는다. 이럴 때는 등급이 높은 쪽(마스터&gt;시니어&gt;주니어)을 앞에 둔다.
     * 안정 정렬이라 점수와 등급이 모두 같으면 LLM이 준 순서가 그대로 유지된다.
     */
    private List<RankedFreelancer> breakScoreTiesByGrade(List<RankedFreelancer> candidates) {
        Map<Long, FreelancerGrade> gradeByFreelancerId = candidates.stream()
                .collect(Collectors.toMap(RankedFreelancer::freelancerId,
                        candidate -> freelancerDirectoryPort.findCardSummary(candidate.freelancerId()).grade()));

        Comparator<RankedFreelancer> byScoreThenGrade = Comparator
                .comparingDouble(RankedFreelancer::score).reversed()
                .thenComparing(candidate -> gradeByFreelancerId.get(candidate.freelancerId()).ordinal(),
                        Comparator.reverseOrder());

        return candidates.stream().sorted(byScoreThenGrade).toList();
    }

    private boolean persistCandidates(MatchingRound round, Long positionId, int exposeCount,
                                      List<RankedFreelancer> ranked, double gradeWeightPercent,
                                      ProjectPositionSummary position) {
        List<MatchingCandidate> candidates = new ArrayList<>();
        boolean lowScoreWarned = false;
        int exposedCount = 0;
        for (RankedFreelancer item : ranked) {
            MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(round.getId(), positionId,
                    item.freelancerId(), 0.0);
            candidate.applyLlmResult(item.score(), item.reason());
            candidate.applyGradeWeight(gradeWeightPercent);

            GuardVerdict guard = evaluateGuard(item.freelancerId(), position);
            candidate.applyGuard(guard.passed(), guard.reason());

            if (guard.passed()) {
                if (exposedCount < exposeCount) {
                    candidate.expose(++exposedCount);
                } else if (candidate.isBelowQualityThreshold(LOW_SCORE_THRESHOLD)) {
                    lowScoreWarned = true;
                }
            }
            candidates.add(candidate);
        }
        matchingCandidateRepository.saveAll(candidates);
        return lowScoreWarned;
    }

    /** Stage F 가드: 직무·스킬만 재검증한다(R02.3). 가드에 떨어져도 후보 기록은 남기고 노출만 안 한다. */
    private GuardVerdict evaluateGuard(Long freelancerId, ProjectPositionSummary position) {
        FreelancerConditionResponse condition = freelancerDirectoryPort.findCondition(freelancerId);

        if (condition.jobRole() != position.jobRole()) {
            return GuardVerdict.failed("직무 불일치: " + condition.jobRole());
        }

        Set<SkillCode> heldSkills = condition.skills().stream()
                .map(FreelancerConditionResponse.Skill::skillCode)
                .collect(Collectors.toSet());
        List<SkillCode> missingSkills = position.requiredSkills().stream()
                .filter(required -> !heldSkills.contains(required))
                .toList();
        if (!missingSkills.isEmpty()) {
            return GuardVerdict.failed("요구 스킬 미달: " + missingSkills);
        }

        return GuardVerdict.PASSED;
    }

    private record GuardVerdict(boolean passed, String reason) {
        private static final GuardVerdict PASSED = new GuardVerdict(true, null);

        private static GuardVerdict failed(String reason) {
            return new GuardVerdict(false, reason);
        }
    }
}
