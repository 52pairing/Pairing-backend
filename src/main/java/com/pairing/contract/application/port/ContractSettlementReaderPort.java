package com.pairing.contract.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 계약 목록이 필요한 정산 값.
 *
 * <p>계약 카드의 "결제 필요" 배지는 계약 상태가 아니라 <b>정산</b> 상태다. 체결까지 끝난 계약이라도
 * 프리랜서 착수금 수수료를 내지 않으면 프로젝트가 진행중으로 넘어가지 않아(P47) 화면이 그 차이를
 * 보여줘야 한다.
 */
public interface ContractSettlementReaderPort {

    /**
     * 주어진 계약들 중 프리랜서 착수금을 이미 낸 계약의 ID.
     *
     * <p>정산은 계약 체결 시점에 생긴다(P27·P29). 아직 체결되지 않은 계약은 낼 정산 자체가 없어
     * 결과에 들어가지 않는다. 배지는 체결 여부와 함께 봐야 한다.
     */
    Set<Long> findPaidDepositContractIds(Collection<Long> contractIds);

    /**
     * 계약별로 내가 지금 결제할 정산 ID. {@code 계약 ID -> 정산 ID}.
     *
     * <p>배지만으로는 어느 정산을 결제할지 알 수 없어 목록 카드의 결제 버튼이 이 값을 쓴다.
     * 낼 게 없는 계약은 결과에 없다. 계약에 걸린 정산은 전부 프리랜서 몫이라 클라이언트가
     * 부르면 빈 맵이다.
     */
    Map<Long, Long> findPayableSettlementIds(Long payerAccountId, Collection<Long> contractIds);
}
