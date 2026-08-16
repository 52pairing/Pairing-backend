package com.pairing.contract.application.service;

import com.pairing.contract.application.event.ContractCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 계약이 만들어지면 본문 문구 채우기를 시작한다.
 *
 * <p>실제 작업은 {@link ContractDraftFiller} 에 있다. 여기는 진입점일 뿐이다 — 5분 주기
 * 복구 스케줄러도 같은 filler 를 부르므로 채우는 규칙이 한 곳에만 있다.
 *
 * <p><b>{@code @Async} 를 여기 붙이지 않는다.</b> filler 쪽에 있고 이 리스너는 그걸 부르기만
 * 하므로 즉시 반환한다. {@code @TransactionalEventListener} 는 커밋 <i>후</i>에 돌지만
 * <b>같은 스레드</b>라, 여기서 기다리면 협상 타결 응답이 AI 호출만큼(최대 120초) 늦어진다.
 *
 * <p>그래서 계약은 <b>DRAFT 로 먼저 보인다.</b> 화면은 그 상태를 "계약서 준비 중"으로 처리하고
 * 서명 버튼을 막는다. 문구가 채워지면 CONTRACT_CREATED 알림이 간다.
 */
@Component
@RequiredArgsConstructor
public class ContractDraftListener {

    private final ContractDraftFiller draftFiller;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ContractCreatedEvent event) {
        draftFiller.fill(event.contractId());
    }
}
