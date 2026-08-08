package com.pairing.project.application.event;

/**
 * 착수금 결제가 끝나 모집이 시작됐다. 결제 트랜잭션이 커밋된 뒤에 전달된다.
 *
 * <p>매칭이 이 신호로 임베딩 저장과 최초 추천을 시작한다. AI 서버를 호출하는 작업이라
 * 결제와 같은 트랜잭션에 두지 않는다. 실패해도 결제와 모집 상태는 그대로 유지된다.
 *
 * <p>받는 쪽은 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 를 쓴다.
 * 커밋된 트랜잭션은 이미 끝났으므로 저장이 필요하면 {@code REQUIRES_NEW} 로 새로 열어야 한다.
 */
public record RecruitingStartedEvent(Long projectId) {
}
