package com.pairing.settlement.application.usecase;

import com.pairing.settlement.application.command.CreateSuccessFeeSettlementCommand;

/**
 * 프로젝트 완료 처리가 쓰는 인바운드 포트.
 *
 * <p>{@link DepositSettlementUseCase} 와 같은 이유로 구현이 project 를 참조하지 않는다.
 * 프로젝트가 정산을 만들고 정산이 프로젝트 상태를 바꾸는 양방향이라, 한 서비스에 두면 생성자 순환이 생긴다.
 */
public interface SuccessFeeSettlementUseCase {

    /**
     * 클라이언트 성공보수 정산 생성. 프로젝트가 완료 대기로 넘어간 시점에 발생한다. (P30)
     *
     * @return 생성된 정산 ID. 화면 결제 버튼이 이 값을 쓴다
     */
    Long createClientSuccessFee(CreateSuccessFeeSettlementCommand command);
}
