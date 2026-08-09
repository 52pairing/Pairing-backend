package com.pairing.settlement.application.service;

import com.pairing.settlement.application.command.CreateSuccessFeeSettlementCommand;
import com.pairing.settlement.application.usecase.SuccessFeeSettlementUseCase;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.domain.service.SuccessFeePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
