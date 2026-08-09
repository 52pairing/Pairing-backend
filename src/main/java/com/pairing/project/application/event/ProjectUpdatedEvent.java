package com.pairing.project.application.event;

/**
 * 모집이 시작된 뒤 프로젝트 정보가 바뀌었다. 수정 트랜잭션이 커밋된 뒤에 전달된다.
 *
 * <p>매칭이 이 신호로 포지션 임베딩을 다시 올린다. 담당업무·업무범위 같은 텍스트가 벡터에 들어가 있어
 * 수정하면 저장된 벡터가 낡는다. 예산은 매칭이 매번 조회해 쓰므로 이 신호와 무관하다.
 *
 * <p>{@code REGISTERED} 상태의 수정에서는 발행하지 않는다. 임베딩은 착수금 결제 시점에 처음 만들어져
 * 그 전에는 갱신할 대상이 없다.
 *
 * <p>받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 를 쓴다.
 * 실패해도 수정 자체는 그대로 유지된다.
 */
public record ProjectUpdatedEvent(Long projectId) {
}
