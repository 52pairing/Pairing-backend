package com.pairing.matching.application.service;

import com.pairing.freelancer.domain.model.FreelancerGrade;
import com.pairing.matching.application.port.out.FreelancerDirectoryPort;
import com.pairing.matching.application.port.out.MatchingPort;
import com.pairing.matching.application.port.out.ProjectDirectoryPort;
import com.pairing.matching.application.result.MatchingRecommendation;
import com.pairing.matching.application.result.RankedFreelancer;
import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.RecommendationType;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 회차(라운드) 생성: Stage C~E(AI 서버 호출) -&gt; Stage F(가드) -&gt; 저장까지 한 번에 처리한다.
 *
 * <p>재추천(2일차)과 최초 추천(결제 완료 트리거, 3일차 예정) 둘 다 이 서비스를 공유한다.
 *
 * <p><b>알아둘 단순화 2가지(2026-08-07)</b>:
 * <ul>
 *   <li>similarity: {@code MatchingPort.recommend()}가 Pairing-python 내부에서 Stage C~E를 한 번에
 *       처리해 최종 순위만 돌려주므로, Spring이 별도로 받는 유사도 값이 없다. 0.0을 임시로 채운다
 *       (참고용 필드라 랭킹/응답에는 안 쓰인다).</li>
 *   <li>Stage F 가드: 규칙 기반 재검증(직무·스킬, 예산 조합) 알고리즘은 아직 없어 항상 통과 처리한다.
 *       3일차에 {@code applyGuard} 호출부만 실제 검증으로 교체하면 된다.</li>
 * </ul>
 * 이전에 노출됐던 프리랜서(R02 예외조건 5, 프로젝트 전체 기준) 제외는 Pairing-python이 벡터 검색
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

    MatchingRound createRound(Long projectId, Long positionId, RecommendationType roundType, int recruitCount,
                              long costAmount) {
        int roundNo = (int) matchingRoundRepository.countByPositionId(positionId) + 1;
        int poolSize = recruitCount * POOL_MULTIPLIER;
        Integer requestedCount = roundType == RecommendationType.PAID ? recruitCount : null;

        MatchingRound round = MatchingRound.create(projectId, positionId, roundNo, roundType, requestedCount,
                costAmount, recruitCount, poolSize);
        round = matchingRoundRepository.save(round);

        List<Long> excludedFreelancerIds = matchingCandidateRepository.findFreelancerIdsByProjectId(projectId);
        MatchingRecommendation recommendation =
                matchingPort.recommend(positionId, recruitCount, POOL_MULTIPLIER, excludedFreelancerIds);

        if (recommendation.candidates().isEmpty()) {
            round.exhaust();
            return matchingRoundRepository.save(round);
        }

        List<RankedFreelancer> ranked = breakScoreTiesByGrade(recommendation.candidates());

        double gradeWeightPercent = clientGradeResolver.resolveMatchingWeightPercent(projectId);
        boolean lowScoreWarned = persistCandidates(round, positionId, recruitCount, ranked, gradeWeightPercent);

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
                                      List<RankedFreelancer> ranked, double gradeWeightPercent) {
        List<MatchingCandidate> candidates = new ArrayList<>();
        boolean lowScoreWarned = false;
        int rank = 1;
        for (RankedFreelancer item : ranked) {
            MatchingCandidate candidate = MatchingCandidate.createFromEmbedding(round.getId(), positionId,
                    item.freelancerId(), 0.0);
            candidate.applyLlmResult(item.score(), item.reason());
            candidate.applyGradeWeight(gradeWeightPercent);
            candidate.applyGuard(true, null);

            if (rank <= exposeCount) {
                candidate.expose(rank);
            } else if (candidate.isBelowQualityThreshold(LOW_SCORE_THRESHOLD)) {
                lowScoreWarned = true;
            }
            candidates.add(candidate);
            rank++;
        }
        matchingCandidateRepository.saveAll(candidates);
        return lowScoreWarned;
    }
}
