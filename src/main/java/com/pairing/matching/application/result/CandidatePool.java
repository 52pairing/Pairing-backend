package com.pairing.matching.application.result;

import java.util.List;

/** GET /embeddings/positions/{id}/candidates 결과 (Stage C~D: 1차 추림). */
public record CandidatePool(Long positionId, List<ScoredFreelancer> candidates) {
}
