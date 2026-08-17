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

        // 클라 착수금은 모집 시작(P27), 프리 착수금은 전원이 다 냈을 때 진행중(P27),
        // 성공보수는 클라·프리 양쪽이 다 냈을 때 종료(P30) 다.
        if (saved.startsRecruiting()) {
            projectRecruitStarterPort.startRecruiting(saved.getProjectId());
        } else if (saved.mayCloseProject()) {
            closeIfSettled(saved.getProjectId());
        } else if (saved.mayStartProgress()) {
            startProgressIfSettled(saved.getProjectId());
        }
        return SettlementResult.from(saved);
    }

    /**
     * 그 프로젝트의 성공보수가 클라이언트·프리랜서 모두 결제됐으면 종료로 넘긴다. (P30)
     *
     * <p>한쪽만 냈을 때 종료하면 나머지 한쪽의 결제 버튼이 화면에서 사라진다.
     * 방금 결제한 건은 이미 PAID 라 미결제로 잡히지 않는다.
     * {@link #startProgressIfSettled} 와 같은 구조다.
     */
    private void closeIfSettled(Long projectId) {
        if (settlementRepository.existsUnpaidSuccessFee(projectId)) {
            return;
        }
        projectCloserPort.close(projectId);
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
