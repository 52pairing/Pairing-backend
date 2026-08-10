package com.pairing.settlement.application.service;

import com.pairing.client.domain.model.ClientGrade;
import com.pairing.settlement.application.command.CreateDepositSettlementCommand;
import com.pairing.settlement.application.usecase.DepositSettlementUseCase;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.domain.service.DepositFeePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 프로젝트 도메인이 호출하는 정산 생성·조회.
 *
 * <p>이 서비스는 project 를 참조하지 않는다. 결제 후 프로젝트 상태를 바꾸는 쪽은
 * {@link SettlementPaymentService} 로 분리했다. 한 클래스에 두면 생성자 순환이 생긴다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class DepositSettlementService implements DepositSettlementUseCase {

    private final SettlementRepository settlementRepository;

    @Override
    public Long createClientDeposit(CreateDepositSettlementCommand command) {
        BigDecimal feeRate = DepositFeePolicy.feeRate(command.budgetAmount());
        BigDecimal gradeDiscount = DepositFeePolicy.gradeDiscount(command.clientGrade());
        long feeAmount = DepositFeePolicy.feeAmount(command.budgetAmount(), feeRate, gradeDiscount);

        Settlement saved = settlementRepository.save(Settlement.createClientDeposit(
                command.projectId(), command.payerAccountId(), command.budgetAmount(),
                feeRate, gradeDiscount, feeAmount));

        // 정산번호는 채번된 id 를 쓴다. 같은 트랜잭션이라 임시번호는 커밋 전에 덮어써진다.
        saved.assignNo(LocalDate.now().getYear());
        return settlementRepository.save(saved).getId();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> findPayableSettlementId(Long projectId) {
        return settlementRepository.findPayableByProjectId(projectId).map(Settlement::getId);
    }

    @Override
    public void recalculateClientDeposit(Long projectId, long budgetAmount, ClientGrade clientGrade) {
        Optional<Settlement> found = settlementRepository.findPayableByProjectId(projectId);
        if (found.isEmpty()) {
            return;
        }

        Settlement settlement = found.get();
        if (settlement.getPhase() != SettlementPhase.DEPOSIT) {
            return;
        }

        BigDecimal feeRate = DepositFeePolicy.feeRate(budgetAmount);
        BigDecimal gradeDiscount = DepositFeePolicy.gradeDiscount(clientGrade);

        settlement.reprice(budgetAmount, feeRate, gradeDiscount,
                DepositFeePolicy.feeAmount(budgetAmount, feeRate, gradeDiscount));
        settlementRepository.save(settlement);
    }

    @Override
    public void cancelPayable(Long projectId) {
        settlementRepository.findPayableByProjectId(projectId).ifPresent(settlement -> {
            settlement.cancel();
            settlementRepository.save(settlement);
        });
    }
}
