package com.pairing.matching.application.service;

import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.NegotiationPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.MatchingRecommendation;
import com.pairing.matching.application.result.ProjectPositionSummary;
import com.pairing.matching.application.result.RankedFreelancer;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 회차(라운드) 생성: Stage C~E(AI 서버 호출) -&gt; Stage F(가드) -&gt; 저장까지 한 번에 처리한다.
 *
 * <p>재추천(2일차)과 최초 추천(결제 완료 트리거, 3일차 예정) 둘 다 이 서비스를 공유한다.
 *
 * <p><b>Stage F 가드 — G3·G4만 (2026-08-12 재설계)</b>
 * <ul>
 *   <li><b>G4</b>({@link LlmResponseGuard}) — LLM 응답 이상(중복 ID / 인원 초과 / 근거 누락).
 *       <b>실제로 후보를 거르는 곳은 여기뿐이다.</b></li>
 *   <li><b>G3</b>({@code evaluateBudgetCombination}) — 예산 조합. <b>탈락시키지 않고 사유만
 *       기록한다.</b></li>
 * </ul>
 *
 * <p><b>직무·스킬 재검증은 뺐다.</b> 하드필터를 통과한 사람만 LLM에 가고 LLM은 그 풀 안에서만
 * 고르므로, 가드에 도착한 후보는 이미 직무·스킬을 통과한 사람이다. 같은 걸 또 봐도 아무도 안 걸린다.
 * (명세 R02.3이 "가드 AI로 마지막 검증(직무, 스킬 검증)"이라 글자상 어긋나는데, 설명은 "직무·스킬은
 * 1차 필터에서 보장하고 가드는 예산 조합과 LLM 응답 이상을 막는다"로 한다 — `.ai/STATE.md` 참고.)
 *
 * <p>이전에 노출됐던 프리랜서(R02 예외조건 5, 프로젝트 전체 기준) 제외는 Pairing-python이 벡터 검색
 * 전에 미리 걸러준다(2026-08-09) — 여기서는 그 목록을 조회해서 넘기기만 한다.
 */
@Component
@RequiredArgsConstructor
class MatchingRoundCreationService {

    static final int POOL_MULTIPLIER = 3;
    private static final double LOW_SCORE_THRESHOLD = 50.0;

    /**
     * G3 예산 조합의 허용 오차 20%(= x1.2). 개인 상한이 아니라 <b>조합 총액</b>에 정의된 값이다
     * (설계 메모 §3 "조합 총액 ≤ 순예산 x 1.2"). 정수 연산으로 두는 건 원 단위 금액에 double을
     * 쓰면 큰 금액에서 오차가 눈에 보이기 때문이다.
     */
    private static final long BUDGET_TOLERANCE_NUMERATOR = 12;
    private static final long BUDGET_TOLERANCE_DENOMINATOR = 10;

    /**
     * 협상이 타결돼 <b>합의 금액이 존재하는</b> 상태들. 이 상태에서만 타결가를 조회한다 —
     * 그 전에 부르면 협상 도메인이 NOT_AGREED 예외를 던진다.
     */
    private static final Set<MatchingStatus> AGREED_STATUSES = Set.of(
            MatchingStatus.CONTRACT_PENDING, MatchingStatus.CONTRACTED, MatchingStatus.IN_PROGRESS,
            MatchingStatus.COMPLETION_PENDING, MatchingStatus.CLOSED);

    private final MatchingPort matchingPort;
    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingCandidateRepository matchingCandidateRepository;
    private final MatchingRequestRepository matchingRequestRepository;
    private final ProjectDirectoryPort projectDirectoryPort;
    private final FreelancerDirectoryPort freelancerDirectoryPort;
    private final NegotiationPort negotiationPort;
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

        // G4: LLM 응답 이상을 먼저 걸러낸다. 순위 계산·저장 전에 해야 중복 ID가 등급 조회에서
        // 예외를 내거나(Collectors.toMap 키 충돌) 그대로 저장되는 일이 없다.
        List<RankedFreelancer> sane = LlmResponseGuard.sanitize(recommendation.candidates(), recruitCount);
        if (sane.isEmpty()) {
            round.exhaust();
            return matchingRoundRepository.save(round);
        }

        List<RankedFreelancer> ranked = breakScoreTiesByGrade(sane);

        double gradeWeightPercent = clientGradeResolver.resolveMatchingWeightPercent(projectId);
        boolean lowScoreWarned =
                persistCandidates(round, positionId, recruitCount, ranked, gradeWeightPercent, position, budgetCap);

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

    /**
     * 후보를 저장하고 노출을 확정한다.
     *
     * <p><b>노출을 먼저 정하고 그다음에 G3를 판정한다(2026-08-12 재설계).</b> G3는 "노출 후보 전원의
     * 합계"를 보는 포지션 단위 검증이라 후보를 한 명씩 보면서는 판정할 수 없다. 옛 구조(가드를 먼저
     * 보고 통과한 사람만 노출)와 순서가 반대다.
     */
    private boolean persistCandidates(MatchingRound round, Long positionId, int exposeCount,
                                      List<RankedFreelancer> ranked, double gradeWeightPercent,
                                      ProjectPositionSummary position, long budgetCap) {
        List<MatchingCandidate> candidates = new ArrayList<>();
        List<Long> exposedFreelancerIds = new ArrayList<>();
        boolean lowScoreWarned = false;
        int exposedCount = 0;

        for (RankedFreelancer item : ranked) {
            MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(round.getId(), positionId,
                    item.freelancerId(), item.similarity());
            candidate.applyLlmResult(item.score(), item.reason());
            candidate.applyGradeWeight(gradeWeightPercent);

            if (exposedCount < exposeCount) {
                candidate.expose(++exposedCount);
                exposedFreelancerIds.add(item.freelancerId());
            } else if (candidate.isBelowQualityThreshold(LOW_SCORE_THRESHOLD)) {
                lowScoreWarned = true;
            }
            candidates.add(candidate);
        }

        // G3: 노출이 확정된 뒤에야 조합 합계를 낼 수 있다. 탈락시키지 않고 사유만 남긴다.
        String budgetWarning = evaluateBudgetCombination(positionId, position, budgetCap, exposedFreelancerIds);
        for (MatchingCandidate candidate : candidates) {
            candidate.applyGuard(true, candidate.isExposed() ? budgetWarning : null);
        }

        matchingCandidateRepository.saveAll(candidates);
        return lowScoreWarned;
    }

    /**
     * 가드 G3 — 예산 조합. 노출 후보 전원의 월단가 합계가 남은 예산 안에 드는지 본다.
     *
     * <pre>
     * 이 포지션 몫 월예산 = budgetCap x 모집 인원
     * 이미 쓴 것          = Σ(자리를 차지 중인 사람의 월단가)   ← 타결가 우선, 없으면 희망 단가
     * 남은 1인 상한       = (몫 - 이미 쓴 것) ÷ 남은 자리
     * 판정                = Σ(노출 후보 월단가) ≤ 남은 1인 상한 x 노출 인원 x 1.2
     * </pre>
     *
     * <p><b>탈락시키지 않는다.</b> 여기서 배제하면 Stage B 폐기 사유(단가로 거르면 사전검수가 안내한
     * 후보 수와 어긋난다)가 그대로 되살아난다. 개인이 상한 안인지는 조건점수 단가 20점이 이미 본다.
     *
     * <p><b>개인별로 재지 않는 이유</b>: 순예산 2,700만에 시니어 1,100 + 주니어 800 + 800 = 2,700이면
     * 딱 맞는데, 개인 상한 900만으로 재면 시니어가 걸린다. 정책도 "인원별 균등 분배 아님"이다.
     *
     * <p><b>이미 자리를 차지한 인원의 단가를 세는 이유</b>: 안 세면 자리가 찰수록 "항상 여유 있음"으로
     * 나와 경고가 무의미해진다. 3명 중 1명이 1,500만에 계약됐는데 남은 2자리를 900만 기준으로 재는
     * 식이 된다. 최초 추천에서는 차지한 사람이 없어 {@code 남은 1인 상한 == budgetCap}이다.
     *
     * @return 초과했을 때의 사유 문구. 예산 안이면 {@code null}
     */
    private String evaluateBudgetCombination(Long positionId, ProjectPositionSummary position,
                                             long budgetCap, List<Long> exposedFreelancerIds) {
        if (exposedFreelancerIds.isEmpty()) {
            return null;
        }

        List<MatchingRequest> occupied = matchingRequestRepository
                .findByPositionIdAndStatusNotIn(positionId, MatchingStatus.SLOT_RELEASED);
        long spent = occupied.stream().mapToLong(this::resolveMonthlyPay).sum();

        int vacancy = position.headcount() - occupied.size();
        if (vacancy <= 0) {
            // 자리가 없으면 재추천 자체가 MT_018로 막히므로 정상 흐름에선 오지 않는다.
            return "예산 판정 불가: 남은 자리 없음";
        }

        long capPerHead = (budgetCap * position.headcount() - spent) / vacancy;
        long limit = Math.max(0L, capPerHead * exposedFreelancerIds.size()
                * BUDGET_TOLERANCE_NUMERATOR / BUDGET_TOLERANCE_DENOMINATOR);
        long exposedSum = exposedFreelancerIds.stream()
                .mapToLong(id -> MonthlyPayConverter.toMonthlyPay(freelancerDirectoryPort.findCondition(id)))
                .sum();

        if (exposedSum <= limit) {
            return null;
        }
        return ("예산 조합 초과: 합계 %d원 > 상한 %d원 "
                + "(남은 1인 %d원 x 노출 %d명 x 1.2, 확정 %d명 %d원 반영, 희망 단가 기준)")
                .formatted(exposedSum, limit, capPerHead, exposedFreelancerIds.size(), occupied.size(), spent);
    }

    /**
     * 자리를 차지 중인 요청 1건이 실제로 쓰는 월단가.
     *
     * <p>타결 이후면 <b>협상 타결가</b>를 쓴다. 그 전이면 희망 단가인데, 근사가 아니라 정확한 값이다 —
     * 타결가가 아직 존재하지 않거나(협상 전), 애초에 예산 안에 들어와 협상할 금액 자체가 없었던
     * 경우다(협상은 {@code monthlyPay > budgetCap}일 때만 AMOUNT 조건을 만든다).
     *
     * <p>타결 전에 타결가를 조회하면 예외가 나므로 상태로 먼저 거른다.
     */
    private long resolveMonthlyPay(MatchingRequest request) {
        if (AGREED_STATUSES.contains(request.getStatus())) {
            Optional<Long> agreed = negotiationPort.findAgreedMonthlyPay(request.getId());
            if (agreed.isPresent()) {
                return agreed.get();
            }
        }
        return MonthlyPayConverter.toMonthlyPay(freelancerDirectoryPort.findCondition(request.getFreelancerId()));
    }
}
