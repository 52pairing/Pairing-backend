package com.pairing.matching.application.service;

import com.pairing.matching.application.result.RankedFreelancer;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 가드 G4 — LLM 응답 이상 차단. <b>가드에서 실제로 후보를 거르는 곳은 여기뿐이다</b>
 * (G3 예산 조합은 경고만 남긴다). 근거는 `.ai/STATE.md` "[5] 가드 — G4 세부".
 *
 * <p>AI 서버도 "풀 밖 ID는 버린다"는 방어를 갖고 있지만, 그건 <b>지어낸 ID</b>만 본다. 중복·초과·
 * 근거 누락은 풀 안의 정상 ID로도 일어나므로 자바에서 한 번 더 본다.
 *
 * <p>순위 계산·저장보다 <b>먼저</b> 불러야 한다. 중복 ID가 남아 있으면 등급 조회의
 * {@code Collectors.toMap}이 키 충돌로 터진다.
 */
@Slf4j
final class LlmResponseGuard {

    private LlmResponseGuard() {
    }

    /**
     * @param recruitCount 이 회차에서 노출할 인원. LLM이 더 많이 줘도 여기까지만 남긴다.
     */
    static List<RankedFreelancer> sanitize(List<RankedFreelancer> candidates, int recruitCount) {
        List<RankedFreelancer> kept = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        for (RankedFreelancer candidate : candidates) {
            if (candidate.freelancerId() == null) {
                log.warn("[가드 G4] freelancerId 없는 후보를 버린다");
                continue;
            }
            // 같은 사람을 1위와 3위에 두 번 넣는 경우. 앞선 순위를 남기고 뒤엣것을 버린다.
            if (!seen.add(candidate.freelancerId())) {
                log.warn("[가드 G4] 중복 후보를 버린다 (freelancerId={})", candidate.freelancerId());
                continue;
            }
            // reason은 화면에 그대로 뜨는 추천 근거다. 비어 있으면 근거 없는 후보가 노출된다.
            if (candidate.reason() == null || candidate.reason().isBlank()) {
                log.warn("[가드 G4] 추천 근거가 없는 후보를 버린다 (freelancerId={})", candidate.freelancerId());
                continue;
            }
            kept.add(candidate);
        }

        // 요청 인원 초과. 순위는 이미 매겨져 있으므로 뒤를 버리면 된다 — 전량 실패로 처리하면
        // 멀쩡한 상위 후보까지 잃고 재추천 안내까지 가게 되어 과하다.
        int limit = recruitCount * MatchingRoundCreationService.POOL_MULTIPLIER;
        if (kept.size() > limit) {
            log.warn("[가드 G4] 요청({})보다 많은 후보({})를 받아 상위 {}명만 남긴다",
                    limit, kept.size(), limit);
            return List.copyOf(kept.subList(0, limit));
        }
        return kept;
    }
}
