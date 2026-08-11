package com.pairing.settlement.application.usecase;

import com.pairing.settlement.application.command.CreateFreelancerSuccessFeeCommand;
import com.pairing.settlement.application.command.CreateSuccessFeeSettlementCommand;

/**
 * 프로젝트 완료 처리가 쓰는 인바운드 포트.
 *
 * <p>{@link DepositSettlementUseCase} 와 같은 이유로 구현이 project 를 참조하지 않는다.
 * 프로젝트가 정산을 만들고 정산이 프로젝트 상태를 바꾸는 양방향이라, 한 서비스에 두면 생성자 순환이 생긴다.
 *
 * <p>두 성공보수는 <b>부르는 쪽이 다르다.</b> 클라이언트 분은 프로젝트가 완료 처리 시 직접 만들고,
 * 프리랜서 분은 계약 도메인이 완료 이벤트를 받아 계약별로 만든다. 누가 프리랜서인지는 계약만 알고,
 * 프로젝트가 계약을 읽으면 참조 순환이 생기기 때문이다.
 */
public interface SuccessFeeSettlementUseCase {

    /**
     * 클라이언트 성공보수 정산 생성. 프로젝트가 완료 대기로 넘어간 시점에 발생한다. (P30)
     *
     * @return 생성된 정산 ID. 화면 결제 버튼이 이 값을 쓴다
     */
    Long createClientSuccessFee(CreateSuccessFeeSettlementCommand command);

    /**
     * 프리랜서 성공보수 정산 생성. 같은 시점이며 계약 도메인이 계약 1건마다 부른다. (P30)
     *
     * <p>계약 1건당 1건이다. 같은 계약으로 다시 부르면 새로 만들지 않고 기존 ID 를 돌려준다.
     *
     * @return 생성된(또는 이미 있던) 정산 ID
     */
    Long createFreelancerSuccessFee(CreateFreelancerSuccessFeeCommand command);
}
