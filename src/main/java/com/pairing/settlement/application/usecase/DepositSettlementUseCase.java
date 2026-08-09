package com.pairing.settlement.application.usecase;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.settlement.application.command.CreateDepositSettlementCommand;

import java.util.Optional;

/**
 * 프로젝트 도메인이 쓰는 인바운드 포트.
 *
 * <p>이 포트의 구현은 project 를 참조하지 않는다. 프로젝트가 정산을 만들고, 정산이 프로젝트 상태를 바꾸는
 * 양방향이라 한 서비스에 두면 생성자 순환이 생긴다.
 */
public interface DepositSettlementUseCase {

    /**
     * 클라이언트 착수금 정산 생성. 프로젝트 등록 완료 시점에 발생한다. (P27·P32)
     *
     * @return 생성된 정산 ID. 화면 결제 버튼이 이 값을 쓴다
     */
    Long createClientDeposit(CreateDepositSettlementCommand command);

    /** 프로젝트에 걸린 결제 대기 정산 ID. 결제할 게 없으면 empty. */
    Optional<Long> findPayableSettlementId(Long projectId);

    /**
     * 예산이 바뀌었을 때 미결제 착수금 정산을 다시 계산한다.
     *
     * <p>이미 결제된 정산은 건드리지 않는다. 프로젝트 쪽에서 결제 후 예산 변경을 막고 있어
     * 실제로는 PENDING 상태만 대상이 된다. 대상이 없으면 아무 일도 하지 않는다.
     */
    void recalculateClientDeposit(Long projectId, long budgetAmount, ClientGrade clientGrade);
}
