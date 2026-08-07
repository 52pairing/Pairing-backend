package com.pairing.matching.application.result;

import java.util.List;

/** POST /matchings/recommendations 결과 (Stage E: LLM 최종 선정). */
public record MatchingRecommendation(Long positionId, String model, List<RankedFreelancer> candidates) {
}
