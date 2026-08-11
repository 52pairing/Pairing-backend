package com.pairing.settlement.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.settlement.application.event.ProjectProgressStartedEvent;
import com.pairing.settlement.application.port.ProjectCloserPort;
import com.pairing.settlement.application.port.ProjectProgressStarterPort;
import com.pairing.settlement.application.port.ProjectRecruitStarterPort;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.application.usecase.SettlementPaymentUseCase;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.exception.SettlementErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ProjectProgressStarterPort projectProgressStarterPort;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public SettlementResult pay(Long settlementId, Long accountId, Long paymentMethodId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        if (!settlement.isPayableBy(accountId)) {
            throw new BusinessException(SettlementErrorCode.NOT_PAYER);
        }

        settlement.pay(paymentMethodId);
        Settlement saved = settlementRepository.save(settlement);

        // 클라 착수금은 모집 시작(P27), 클라 성공보수는 종료(P30),
        // 프리 착수금은 전원이 다 냈을 때 진행중(P27) 이다. 프리 성공보수는 상태를 바꾸지 않는다.
        if (saved.startsRecruiting()) {
            projectRecruitStarterPort.startRecruiting(saved.getProjectId());
        } else if (saved.closesProject()) {
            projectCloserPort.close(saved.getProjectId());
        } else if (saved.mayStartProgress()) {
            startProgressIfSettled(saved.getProjectId());
        }
        return SettlementResult.from(saved);
    }

    /**
     * 프로젝트의 프리랜서 착수금이 전부 결제됐으면 진행중으로 넘긴다.
     *
     * <p>인원이 덜 찼으면 프로젝트 쪽에서 막는다. 3명 중 2명만 계약한 상태에서 그 2명이 수수료를
     * 내도 진행중이 되면 안 되기 때문이다.
     *
     * <p>실제로 넘어갔을 때만 이벤트를 낸다. 인원별 상태를 옮기는 쪽(매칭)이 이걸 듣는다.
     */
    private void startProgressIfSettled(Long projectId) {
        if (settlementRepository.existsUnpaidFreelancerDeposit(projectId)) {
            return;
        }
        if (projectProgressStarterPort.startProgress(projectId)) {
            eventPublisher.publishEvent(new ProjectProgressStartedEvent(projectId));
        }
    }
}
