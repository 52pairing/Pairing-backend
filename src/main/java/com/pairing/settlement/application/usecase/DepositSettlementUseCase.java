package com.pairing.settlement.application.usecase;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.settlement.application.command.CreateDepositSettlementCommand;
import com.pairing.settlement.application.command.CreateFreelancerDepositCommand;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

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

    /**
     * 프리랜서 착수금 정산 생성. 계약 체결(양측 서명 완료) 시점에 발생한다. (P27·P29)
     *
     * <p>계약 1건당 1건이다. 같은 계약으로 다시 부르면 새로 만들지 않고 기존 ID 를 돌려준다.
     *
     * @return 생성된(또는 이미 있던) 정산 ID
     */
    Long createFreelancerDeposit(CreateFreelancerDepositCommand command);

    /**
     * 프로젝트의 프리랜서 착수금이 전부 결제됐는지.
     *
     * <p>전원 계약 완료 + 착수금 결제까지 끝나야 프로젝트가 진행중으로 넘어간다. 아직 만들어진
     * 정산이 하나도 없으면 낼 것도 없으므로 true 다. 호출부가 인원 확정 여부를 함께 본다.
     */
    boolean isFreelancerDepositSettled(Long projectId);

    /**
     * 주어진 계약들 중 프리랜서 착수금을 이미 낸 계약의 ID.
     *
     * <p>계약 목록 화면이 계약마다 "결제 필요" 배지를 띄울지 판단한다. 계약마다 되물으면 페이지
     * 크기만큼 쿼리가 늘어나서 한 번에 받는다.
     *
     * <p>정산은 <b>계약 체결 시점에 생긴다</b>(P27·P29). 아직 체결되지 않은 계약은 낼 정산 자체가
     * 없어 결과에 안 들어간다. 호출부가 체결 여부를 함께 봐야 한다.
     */
    Set<Long> findPaidFreelancerDepositContractIds(Collection<Long> contractIds);

    /** 프로젝트에 걸린 결제 대기 정산 ID. 결제할 게 없으면 empty. */
    Optional<Long> findPayableSettlementId(Long projectId);

    /**
     * 예산이 바뀌었을 때 미결제 착수금 정산을 다시 계산한다.
     *
     * <p>이미 결제된 정산은 건드리지 않는다. 프로젝트 쪽에서 결제 후 예산 변경을 막고 있어
     * 실제로는 PENDING 상태만 대상이 된다. 대상이 없으면 아무 일도 하지 않는다.
     */
    void recalculateClientDeposit(Long projectId, long budgetAmount, ClientGrade clientGrade);

    /**
     * 프로젝트 등록이 취소돼 낼 이유가 사라진 정산을 취소한다.
     *
     * <p>결제 대기 중인 건만 대상이다. 이미 결제된 건은 건드리지 않는다.
     * 대상이 없으면 아무 일도 하지 않는다.
     */
    void cancelPayable(Long projectId);
}
