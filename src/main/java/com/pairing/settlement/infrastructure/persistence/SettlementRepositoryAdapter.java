package com.pairing.settlement.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.settlement.domain.repository.SettlementRepository;
import com.pairing.settlement.infrastructure.mapper.SettlementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    public Optional<Settlement> findByContractId(Long contractId) {
        return springDataRepository.findByContractId(contractId).map(settlementMapper::toDomain);
    }

    @Override
    public Page<Settlement> findByPayer(Long payerAccountId, Long projectId, SettlementPhase phase,
                                        SettlementStatus status, Pageable pageable) {
        return springDataRepository.findByPayer(payerAccountId, projectId, phase, status, pageable)
                .map(settlementMapper::toDomain);
    }

    @Override
    public Optional<Settlement> findPayableByProjectId(Long projectId) {
        return springDataRepository.findFirstPayable(projectId, PartyRole.CLIENT, PAYABLE_STATUSES)
                .map(settlementMapper::toDomain);
    }

    @Override
    public boolean existsUnpaidByPayer(Long payerAccountId) {
        return springDataRepository.existsByPayerAccountIdAndStatusIn(payerAccountId, PAYABLE_STATUSES);
    }

    @Override
    public boolean existsUnpaidFreelancerDeposit(Long projectId) {
        return springDataRepository.existsByProjectIdAndPayerRoleAndPhaseAndStatusIn(
                projectId, PartyRole.FREELANCER, SettlementPhase.DEPOSIT, PAYABLE_STATUSES);
    }

    /** 빈 목록으로 부르면 {@code IN ()} 이 되어 DB 가 거부한다. 쿼리를 태우지 않고 바로 돌려준다. */
    @Override
    public Set<Long> findPaidFreelancerDepositContractIds(Collection<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Set.of();
        }
        return springDataRepository.findPaidFreelancerDepositContractIds(contractIds).stream()
                .collect(Collectors.toUnmodifiableSet());
    }
}
