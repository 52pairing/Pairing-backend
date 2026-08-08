package com.pairing.project.infrastructure;

import com.pairing.project.application.port.SettlementReaderPort;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** SettlementReaderPort 구현. 정산 도메인의 인바운드 포트를 호출한다. */
@Component
@RequiredArgsConstructor
public class SettlementReaderAdapter implements SettlementReaderPort {

    private final DepositSettlementUseCase depositSettlementUseCase;

    @Override
    public Optional<Long> findPayableSettlementId(Long projectId) {
        return depositSettlementUseCase.findPayableSettlementId(projectId);
    }
}
