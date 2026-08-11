package com.pairing.freelancer.application.event;

/**
 * 프리랜서 조건(직무·스킬·경력·근무방식·기간 등)이 저장됐다. 저장 트랜잭션이 커밋된 뒤에 전달된다.
 *
 * <p>매칭이 이 신호로 프리랜서 임베딩을 다시 올린다. 2026-08-11부터 이 값들이 임베딩 텍스트에
 * 들어가므로({@code matching.application.service.FreelancerEmbeddingTextBuilder}), 조건을 고치면
 * 저장된 벡터가 낡는다 — 예전엔 자기소개+경력사항만 넣어서 조건을 바꿔도 벡터가 그대로였다.
 *
 * <p>받는 쪽은 {@link ResumeUpdatedEvent}와 같은 규칙이다:
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}로 받고, 실패해도 조건 저장 자체는
 * 그대로 유지된다.
 */
public record ConditionUpdatedEvent(Long accountId) {
}
