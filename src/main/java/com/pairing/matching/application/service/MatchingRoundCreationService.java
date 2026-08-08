package com.pairing.matching.application.service;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
 * 이전에 노출됐던 프리랜서(R02 예외조건 5, 프로젝트 전체 기준) 제외는 Pairing-python이 아직
 * 하드필터를 안 갖고 있어(3일차 예정) 여기서 결과를 받은 뒤 걸러낸다.
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
    private final ClientGradeResolver clientGradeResolver;

    MatchingRound createRound(Long projectId, Long positionId, RecommendationType roundType, int recruitCount,
                              long costAmount) {
        int roundNo = (int) matchingRoundRepository.countByPositionId(positionId) + 1;
        int poolSize = recruitCount * POOL_MULTIPLIER;
        Integer requestedCount = roundType == RecommendationType.PAID ? recruitCount : null;

        MatchingRound round = MatchingRound.create(projectId, positionId, roundNo, roundType, requestedCount,
                costAmount, recruitCount, poolSize);
        round = matchingRoundRepository.save(round);

        MatchingRecommendation recommendation = matchingPort.recommend(positionId, recruitCount, POOL_MULTIPLIER);
        List<RankedFreelancer> filtered = excludePreviouslySurfaced(projectId, recommendation.candidates());

        if (filtered.isEmpty()) {
            round.exhaust();
            return matchingRoundRepository.save(round);
        }

        double gradeWeightPercent = clientGradeResolver.resolveMatchingWeightPercent(projectId);
        boolean lowScoreWarned = persistCandidates(round, positionId, recruitCount, filtered, gradeWeightPercent);

        if (lowScoreWarned) {
            round.warnLowScore();
        }
        round.complete();
        return matchingRoundRepository.save(round);
    }

    private List<RankedFreelancer> excludePreviouslySurfaced(Long projectId, List<RankedFreelancer> candidates) {
        Set<Long> excluded = new HashSet<>(matchingCandidateRepository.findFreelancerIdsByProjectId(projectId));
        return candidates.stream().filter(candidate -> !excluded.contains(candidate.freelancerId())).toList();
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
            candidate.applyGuard(true, null);
            candidate.applyGradeWeight(gradeWeightPercent);

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
