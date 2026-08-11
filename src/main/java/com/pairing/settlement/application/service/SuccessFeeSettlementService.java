package com.pairing.settlement.application.service;

import com.pairing.settlement.application.command.CreateFreelancerSuccessFeeCommand;
import com.pairing.settlement.application.command.CreateSuccessFeeSettlementCommand;
import com.pairing.settlement.application.usecase.SuccessFeeSettlementUseCase;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.domain.service.SuccessFeePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * 프로젝트 완료 처리가 호출하는 성공보수 정산 생성.
 *
 * <p>{@link DepositSettlementService} 와 같은 이유로 project 를 참조하지 않는다.
 * 결제 후 프로젝트 상태를 바꾸는 쪽은 {@link SettlementPaymentService} 다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class SuccessFeeSettlementService implements SuccessFeeSettlementUseCase {

    private final SettlementRepository settlementRepository;

    @Override
    public Long createClientSuccessFee(CreateSuccessFeeSettlementCommand command) {
        BigDecimal feeRate = SuccessFeePolicy.feeRate(command.baseAmount());
        BigDecimal gradeDiscount = SuccessFeePolicy.gradeDiscount(command.clientGrade());
        long feeAmount = SuccessFeePolicy.feeAmount(command.baseAmount(), feeRate, gradeDiscount);

        Settlement saved = settlementRepository.save(Settlement.createClientSuccessFee(
                command.projectId(), command.payerAccountId(), command.baseAmount(),
                feeRate, gradeDiscount, feeAmount));

        // 정산번호는 채번된 id 를 쓴다. 같은 트랜잭션이라 임시번호는 커밋 전에 덮어써진다.
        saved.assignNo(LocalDate.now().getYear());
        return settlementRepository.save(saved).getId();
    }

    @Override
    public Long createFreelancerSuccessFee(CreateFreelancerSuccessFeeCommand command) {
        // 계약 1건당 1건. 완료 처리가 재시도되어도 두 번 청구되지 않는다.
        // 같은 계약에 착수금도 붙어 있으므로 단계를 함께 걸어야 단건으로 잡힌다.
        Optional<Settlement> existing = settlementRepository
                .findByContractIdAndPhase(command.contractId(), SettlementPhase.SUCCESS_FEE);
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        BigDecimal feeRate = SuccessFeePolicy.freelancerFeeRate();
        BigDecimal gradeDiscount = SuccessFeePolicy.gradeDiscount(command.freelancerGrade());
        long feeAmount = SuccessFeePolicy.feeAmount(command.contractAmount(), feeRate, gradeDiscount);

        Settlement saved = settlementRepository.save(Settlement.createFreelancerSuccessFee(
                command.projectId(), command.contractId(), command.payerAccountId(),
                command.contractAmount(), feeRate, gradeDiscount, feeAmount));

        saved.assignNo(LocalDate.now().getYear());
        return settlementRepository.save(saved).getId();
    }
}
