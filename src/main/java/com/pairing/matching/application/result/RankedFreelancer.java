package com.pairing.matching.application.result;

/** LLM이 산출한 후보 1건. score는 0~100 스케일의 원점수(base_score, 등급 가중치 반영 전)다. */
public record RankedFreelancer(Long freelancerId, double score, String reason) {
}
