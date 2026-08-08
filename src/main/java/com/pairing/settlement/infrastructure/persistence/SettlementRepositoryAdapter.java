package com.pairing.settlement.infrastructure.persistence;

import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.infrastructure.mapper.SettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SettlementRepositoryAdapter implements SettlementRepository {

    /** 결제 버튼을 열어줘야 하는 상태들. Settlement.isPayable() 과 같은 기준이다. */
    private static final List<SettlementStatus> PAYABLE_STATUSES =
            List.of(SettlementStatus.PENDING, SettlementStatus.OVERDUE, SettlementStatus.FAILED);

    private final SpringDataSettlementRepository springDataRepository;
    private final SettlementMapper settlementMapper;

    @Override
    public Settlement save(Settlement settlement) {
        return settlementMapper.toDomain(
                springDataRepository.save(settlementMapper.toJpaEntity(settlement)));
    }

    @Override
    public Optional<Settlement> findById(Long settlementId) {
        return springDataRepository.findById(settlementId).map(settlementMapper::toDomain);
    }

    @Override
    public Page<Settlement> findByPayer(Long payerAccountId, SettlementPhase phase,
                                        SettlementStatus status, Pageable pageable) {
        return springDataRepository.findByPayer(payerAccountId, phase, status, pageable)
                .map(settlementMapper::toDomain);
    }

    @Override
    public Optional<Settlement> findPayableByProjectId(Long projectId) {
        return springDataRepository.findFirstPayable(projectId, PAYABLE_STATUSES)
                .map(settlementMapper::toDomain);
    }
}
