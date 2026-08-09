package com.pairing.freelancer.application.event;

/**
 * 이력서(자기소개+경력사항)가 저장됐다. 저장 트랜잭션이 커밋된 뒤에 전달된다.
 *
 * <p>매칭이 이 신호로 프리랜서 임베딩을 다시 올린다. 자기소개·경력사항 텍스트가 벡터에 들어가 있어
 * 수정하면 저장된 벡터가 낡는다.
 *
 * <p>받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)}를 쓴다.
 * 실패해도 이력서 저장 자체는 그대로 유지된다.
 */
public record ResumeUpdatedEvent(Long accountId) {
}
