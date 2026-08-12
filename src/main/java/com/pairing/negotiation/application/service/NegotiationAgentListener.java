package com.pairing.negotiation.application.service;

import com.pairing.negotiation.application.event.NegotiationAgentRequested;
import com.pairing.negotiation.application.usecase.NegotiationAgentUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 대리인(A2A) 호출을 사용자 요청 밖으로 빼낸다.
 *
 * <p>같은 패턴이 {@code ContractDraftListener} 에 있다. 다른 점은 <b>여기서 만들어지는 것이
 * 협상의 본 내용</b>(제안·라운드·타결)이라는 것이다. 계약서 문구는 없어도 계약이 DRAFT 로 보이지만,
 * 대리인 제안은 없으면 화면에 아무 일도 일어나지 않는다. 그래서 실패를 반드시 상태로 남긴다
 * ({@code agentState=FAILED}).
 *
 * <p><b>왜 별도 클래스인가.</b> {@code @Async} 와 {@code @TransactionalEventListener} 는 프록시로
 * 동작해서 같은 빈 안에서 자기 메서드를 부르면(self-invocation) 그냥 무시된다. 실행 주체가 달라야
 * 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NegotiationAgentListener {

    private final NegotiationAgentUseCase agentUseCase;

    /**
     * <b>{@code @Async} 가 있어야 사용자 응답이 A2A 를 기다리지 않는다.</b>
     *
     * <p>{@code @TransactionalEventListener} 는 커밋 <i>후</i>에 돌지만 <b>같은 스레드</b>에서
     * 이어 실행된다. 그것만으로는 응답이 여전히 17초 뒤에 나간다.
     *
     * <p>트랜잭션 경계는 {@code runAgent} 쪽에 있다({@code REQUIRES_NEW}). 여기서 열면 이미
     * 커밋된 트랜잭션에 얹으려다 아무 데도 참여하지 못한다.
     *
     * <p><b>실패 표시를 여기서 부르는 이유.</b> {@code runAgent} 안에서 실패를 기록하면 그 기록도
     * 같은 트랜잭션이라 함께 롤백된다. 롤백이 끝난 뒤 <b>바깥에서</b> 새 트랜잭션으로 남겨야 한다.
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(NegotiationAgentRequested event) {
        try {
            agentUseCase.runAgent(event.negotiationId(), event.fallbackType());
        } catch (Exception e) {
            log.error("대리인 실행 실패. 라운드가 오르지 않았다. negotiationId={}", event.negotiationId(), e);
            markFailed(event.negotiationId());
        }
    }

    /**
     * 실패 표시마저 실패하면 협상이 {@code RUNNING} 에 갇힌다. 그 상태는
     * {@code Negotiation.isAgentStuck} 이 다음 요청에서 회수하지만, 원인을 남겨 둬야 추적이 된다.
     */
    private void markFailed(Long negotiationId) {
        try {
            agentUseCase.markAgentFailed(negotiationId);
        } catch (Exception e) {
            log.error("대리인 실패 표시조차 못 했다. 협상이 RUNNING 에 갇힌다. negotiationId={}",
                    negotiationId, e);
        }
    }
}
