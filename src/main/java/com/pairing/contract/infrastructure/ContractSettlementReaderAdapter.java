package com.pairing.contract.infrastructure;

import com.pairing.contract.application.port.ContractSettlementReaderPort;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * ContractSettlementReaderPort 구현. 정산 도메인의 인바운드 포트를 호출한다.
 *
 * <p>계약이 정산을 부르는 방향은 이미 있다(체결 시 착수금 생성). 조회도 같은 방향이라 순환은 없다.
 */
@Component
@RequiredArgsConstructor
public class ContractSettlementReaderAdapter implements ContractSettlementReaderPort {

    private final DepositSettlementUseCase depositSettlementUseCase;
    private final SettlementQueryUseCase settlementQueryUseCase;

    @Override
    public Set<Long> findPaidDepositContractIds(Collection<Long> contractIds) {
        return depositSettlementUseCase.findPaidFreelancerDepositContractIds(contractIds);
    }

    @Override
    public Map<Long, Long> findPayableSettlementIds(Long payerAccountId, Collection<Long> contractIds) {
        return settlementQueryUseCase.findPayableSettlementIdsByContract(payerAccountId, contractIds);
    }
}
