package com.pairing.settlement.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.application.result.MySettlementSummary;
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
import java.util.Map;
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

    /**
     * 단계별 집계를 받아 화면이 쓰는 모양으로 접는다.
     *
     * <p>낸 적이 없으면 쿼리가 0행을 준다. 그때 {@code EMPTY} 로 떨어지므로 화면은 널 검사 없이
     * 0원을 그리면 된다.
     */
    @Override
    public MySettlementSummary sumPaidByPayer(Long payerAccountId) {
        List<PaidSettlementSumRow> rows = springDataRepository.sumPaidByPayerGroupedByPhase(payerAccountId);
        if (rows.isEmpty()) {
            return MySettlementSummary.EMPTY;
        }

        long deposit = sumOf(rows, SettlementPhase.DEPOSIT);
        long successFee = sumOf(rows, SettlementPhase.SUCCESS_FEE);

        return new MySettlementSummary(
                deposit + successFee,
                deposit,
                successFee,
                projectCountOf(rows, SettlementPhase.DEPOSIT),
                projectCountOf(rows, SettlementPhase.SUCCESS_FEE));
    }

    private long sumOf(List<PaidSettlementSumRow> rows, SettlementPhase phase) {
        return rows.stream()
                .filter(row -> row.phase() == phase)
                .mapToLong(row -> row.feeSum() == null ? 0L : row.feeSum())
                .sum();
    }

    private long projectCountOf(List<PaidSettlementSumRow> rows, SettlementPhase phase) {
        return rows.stream()
                .filter(row -> row.phase() == phase)
                .mapToLong(row -> row.projectCount() == null ? 0L : row.projectCount())
                .sum();
    }

    @Override
    public Optional<Settlement> findById(Long settlementId) {
        return springDataRepository.findById(settlementId).map(settlementMapper::toDomain);
    }

    @Override
    public Optional<Settlement> findByContractIdAndPhase(Long contractId, SettlementPhase phase) {
        return springDataRepository.findByContractIdAndPhase(contractId, phase)
                .map(settlementMapper::toDomain);
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
    public List<Settlement> findAllPayableByProjectId(Long projectId) {
        return springDataRepository.findByProjectIdAndStatusIn(projectId, PAYABLE_STATUSES).stream()
                .map(settlementMapper::toDomain)
                .toList();
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

    @Override
    public boolean existsUnpaidSuccessFee(Long projectId) {
        return springDataRepository.existsByProjectIdAndPhaseAndStatusIn(
                projectId, SettlementPhase.SUCCESS_FEE, PAYABLE_STATUSES);
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

    /** 계약 하나에 결제 대기가 둘 이상이면 오래된 것을 남긴다. 쿼리가 id 오름차순이라 첫 값이 그것이다. */
    @Override
    public Map<Long, Long> findPayableSettlementIdsByContract(Long payerAccountId,
                                                              Collection<Long> contractIds) {
        if (contractIds == null || contractIds.isEmpty()) {
            return Map.of();
        }
        return springDataRepository
                .findPayableByContractIds(payerAccountId, contractIds, PAYABLE_STATUSES).stream()
                .collect(Collectors.toUnmodifiableMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1],
                        (first, second) -> first));
    }
}
