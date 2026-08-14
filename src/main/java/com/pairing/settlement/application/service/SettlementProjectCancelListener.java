package com.pairing.settlement.application.service;

import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로젝트가 취소되면 아직 안 낸 수수료를 접는다. (정책 P46)
 *
 * <p>남겨두면 프리랜서 화면에 "결제 필요" 배지가 계속 뜬다. 죽은 프로젝트의 수수료를 내라고
 * 재촉하는 셈이라 결제까지 이어지면 되돌릴 방법도 없다.
 *
 * <p><b>이미 낸 돈은 건드리지 않는다.</b> 환불은 실제 송금이 필요한데 그 처리가 아직 없다.
 * 상태를 바꿔두면 낸 적 없는 것처럼 보여 이력이 어긋나므로 {@code PAID} 로 둔다.
 * 화면이 프로젝트 취소 여부를 보고 "환불 대상" 으로 안내한다.
 *
 * <p>독립 트랜잭션으로 돈다. 여기서 실패해도 프로젝트 취소는 유지돼야 한다 — 되돌리면
 * 스케줄러 로그에는 처리됐다고 남는데 실제로는 아무것도 안 바뀐 상태가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SettlementProjectCancelListener {

    private final DepositSettlementUseCase depositSettlementUseCase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProjectCanceledEvent event) {
        int canceled = depositSettlementUseCase.cancelAllPayable(event.projectId());

        if (canceled > 0) {
            log.info("[프로젝트 취소] 미결제 수수료 {}건을 취소했다. projectId={}", canceled, event.projectId());
        }
    }
}
