package com.pairing.settlement.application.service;

import com.pairing.global.exception.BusinessException;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.application.usecase.SettlementQueryUseCase;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.exception.SettlementErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 정산 조회.
 *
 * <p>프로젝트명 같은 다른 도메인 값은 붙이지 않는다. 여기서 project 를 참조하면
 * project -> settlement 방향과 맞물려 순환이 된다. 조립은 컨트롤러가 한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SettlementQueryService implements SettlementQueryUseCase {

    private final SettlementRepository settlementRepository;

    @Override
    public SettlementResult getByIdForPayer(Long settlementId, Long accountId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new BusinessException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));

        if (!settlement.isPayableBy(accountId)) {
            throw new BusinessException(SettlementErrorCode.NOT_PAYER);
        }
        return SettlementResult.from(settlement);
    }

    @Override
    public Page<SettlementResult> findMine(Long accountId, SettlementPhase phase,
                                           SettlementStatus status, Pageable pageable) {
        return settlementRepository.findByPayer(accountId, phase, status, pageable)
                .map(SettlementResult::from);
    }
}
