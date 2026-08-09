package com.pairing.settlement.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.settlement.application.port.ProjectCloserPort;
import com.pairing.settlement.application.port.ProjectRecruitStarterPort;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.application.usecase.SettlementPaymentUseCase;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.exception.SettlementErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수수료 결제.
 *
 * <p>PG 연동이 없다. 결제수단 ID 를 기록하고 상태를 PAID 로 바꾸는 모의 처리다.
 * 실제 승인·실패 처리는 이 서비스 안에서 교체한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SettlementPaymentService implements SettlementPaymentUseCase {

    private final SettlementRepository settlementRepository;
    private final ProjectRecruitStarterPort projectRecruitStarterPort;
    private final ProjectCloserPort projectCloserPort;

    @Override
    public SettlementResult pay(Long settlementId, Long accountId, Long paymentMethodId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        if (!settlement.isPayableBy(accountId)) {
            throw new BusinessException(SettlementErrorCode.NOT_PAYER);
        }

        settlement.pay(paymentMethodId);
        Settlement saved = settlementRepository.save(settlement);

        // 결제가 프로젝트 상태를 옮기는 경우는 둘뿐이다. 착수금은 모집 시작(P27),
        // 성공보수는 종료(P30) 다. 프리랜서 분은 상태를 바꾸지 않는다.
        if (saved.startsRecruiting()) {
            projectRecruitStarterPort.startRecruiting(saved.getProjectId());
        } else if (saved.closesProject()) {
            projectCloserPort.close(saved.getProjectId());
        }
        return SettlementResult.from(saved);
    }
}
