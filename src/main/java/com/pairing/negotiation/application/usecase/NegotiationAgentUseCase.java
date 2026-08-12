package com.pairing.negotiation.application.usecase;

import com.pairing.negotiation.application.event.NegotiationEvent.NegotiationEventType;

/**
 * 대리인(A2A) 한 라운드 실행. <b>요청 스레드가 아니라 리스너 스레드에서 불린다.</b>
 *
 * <p>{@code NegotiationLoopUseCase} 와 분리한 이유: 저쪽은 컨트롤러가 부르는 사람 행위
 * (마지노선 제출·응답)이고, 이쪽은 그 결과로 커밋 후에 도는 기계 작업이다. 호출자도 트랜잭션
 * 경계도 다르다.
 */
public interface NegotiationAgentUseCase {

    /**
     * 대리인을 한 라운드 돌리고 결과를 저장한다. <b>새 트랜잭션에서 돈다</b> — 호출한 쪽(사람 요청)은
     * 이미 커밋됐다.
     *
     * <p><b>실패하면 예외를 던진다.</b> 삼키지 않는 이유: 실패를 이 트랜잭션 안에서 기록하려 해도
     * 어차피 같이 롤백된다. 실패 표시는 호출자가 {@link #markAgentFailed} 로 <b>별도 트랜잭션</b>에서
     * 해야 한다.
     *
     * @param fallbackType 타결/결렬이 아닐 때 내보낼 실시간 이벤트 종류
     */
    void runAgent(Long negotiationId, NegotiationEventType fallbackType);

    /**
     * 대리인 실행 실패를 남긴다({@code agentState=FAILED} + 안내 메시지 + 실시간 이벤트).
     *
     * <p>{@link #runAgent} 가 롤백된 <b>뒤에</b> 별도 트랜잭션으로 불러야 한다. 이게 없으면 화면은
     * 영원히 "협상 중"에 멈추고, 비동기라 예외가 사용자에게 가지도 않아 아무도 원인을 모른다.
     */
    void markAgentFailed(Long negotiationId);
}
