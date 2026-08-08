package com.pairing.matching.application.result;

/** Pairing-python의 후보 풀 검색 결과 1건. score는 임베딩 코사인 유사도(0~1)이며 추리는 용도로만 쓰고 버린다. */
public record ScoredFreelancer(Long freelancerId, double similarity) {
}
