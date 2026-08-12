package com.pairing.matching.application.result;

/**
 * LLM이 산출한 후보 1건. score는 0~100 스케일의 원점수(base_score, 등급 가중치 반영 전)다.
 *
 * <p>{@code similarity}는 LLM이 만든 값이 아니라 <b>AI 서버가 1차 추림에서 계산한 코사인 유사도</b>
 * (순위 환산 전 원본)다. {@code matching_candidate.similarity}(numeric(6,4))에 그대로 저장한다 —
 * 예전엔 이 자리에 0.0을 박아 넣어서 "이 후보가 왜 뽑혔나"를 나중에 되짚을 수 없었다.
 * 랭킹에는 쓰지 않는다(순위는 LLM의 score가 정한다).
 */
public record RankedFreelancer(Long freelancerId, double score, String reason, double similarity) {
}
