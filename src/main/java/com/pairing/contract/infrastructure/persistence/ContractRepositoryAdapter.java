package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.ContractTab;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.contract.infrastructure.mapper.ContractMapper;
import com.pairing.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 계약 리포지토리 어댑터.
 *
 * <p>서명 저장은 계약 애그리거트의 cascade 로 처리한다.
 */
@Repository
@RequiredArgsConstructor
public class ContractRepositoryAdapter implements ContractRepository {

    /**
     * 체결된 것으로 보는 상태. 인원 충족 판정에 쓴다. 파기·거부는 빠진다.
     *
     * <p>{@link ContractStatus#isConcluded()} 와 같은 집합이다. 여기는 쿼리에 넣을 목록이 필요해
     * 상수로 펼쳐 두는데, 상태를 추가하면 <b>양쪽을 함께</b> 고쳐야 한다. 빠뜨리면 진행중으로
     * 넘어간 계약이 인원수에서 사라져 프로젝트가 다시 모집중으로 되돌아간다.
     */
    private static final List<ContractStatus> SIGNED_STATUSES = Arrays.stream(ContractStatus.values())
            .filter(ContractStatus::isConcluded)
            .toList();

    private final SpringDataContractRepository springDataRepository;
    private final ContractMapper contractMapper;

    /**
     * 저장 후 계약 번호를 채운다.
     *
     * <p>번호에 id 가 들어가 INSERT 전에는 만들 수 없다. 같은 트랜잭션 안에서 덮어쓰므로
     * 번호 없는 행이 외부에 보이지 않는다. 정산번호와 같은 방식이다.
     */
    @Override
    public Contract save(Contract contract) {
        ContractJpaEntity saved = springDataRepository.save(contractMapper.toJpaEntity(contract));

        Contract domain = contractMapper.toDomain(saved);
        domain.assignContractNo(LocalDate.now().getYear());
        saved.applyContractNo(domain.getContractNo());

        return domain;
    }

    @Override
    public Contract updateState(Contract contract) {
        ContractJpaEntity entity = springDataRepository.findById(contract.getId())
                .orElseThrow(() -> new BusinessException(ContractErrorCode.CONTRACT_NOT_FOUND));

        // 영속 엔티티를 그대로 두고 스칼라만 갱신한다. 변경 감지가 커밋 시점에 UPDATE 를 만든다.
        entity.applyState(contractMapper.toJpaEntity(contract));
        applySignatureState(entity, contract);

        return contractMapper.toDomain(entity);
    }

    /** 서명 행을 재생성하지 않고 계정별로 짝지어 상태만 옮긴다. */
    private void applySignatureState(ContractJpaEntity entity, Contract contract) {
        Map<Long, ContractSignatureJpaEntity> persisted = entity.getSignatures().stream()
                .collect(Collectors.toMap(ContractSignatureJpaEntity::getAccountId, Function.identity()));

        contract.getSignatures().forEach(signature -> {
            ContractSignatureJpaEntity target = persisted.get(signature.getAccountId());
            if (target != null) {
                target.applyState(contractMapper.toSignatureEntity(signature));
            }
        });
    }

    @Override
    public Optional<Contract> findById(Long contractId) {
        return springDataRepository.findById(contractId).map(contractMapper::toDomain);
    }

    @Override
    public Optional<Contract> findByNegotiationId(Long negotiationId) {
        return springDataRepository.findByNegotiationId(negotiationId).map(contractMapper::toDomain);
    }

    @Override
    public Page<Contract> findByParty(Long accountId, Long projectId, ContractStatus status,
                                      ContractTab tab, Pageable pageable) {
        // null 은 전체 조회다. ALL 이 전체 상태를 담고 있어 조건이 사실상 걸리지 않는다.
        ContractTab resolved = tab == null ? ContractTab.ALL : tab;

        return springDataRepository.findByParty(accountId, projectId, status,
                        resolved.getMySignatureStatus(), resolved.getStatuses(), pageable)
                .map(contractMapper::toDomain);
    }

    /**
     * 탭 배지. 집계는 한 번만 하고 접는 규칙은 {@link ContractTab} 을 그대로 쓴다.
     *
     * <p>탭 조건을 여기 다시 적으면 목록과 배지가 갈라진다. 실제로 "서명 대기"는 계약 상태만으로
     * 셀 수 없어서 조건을 옮겨 적기 쉬운 자리다 — 그래서 이넘이 들고 있는 값으로만 판정한다.
     * 판정식은 {@code findByParty} 의 WHERE 절과 같은 모양이다.
     */
    @Override
    public Map<ContractTab, Long> countMyTabs(Long accountId, Long projectId) {
        List<ContractStatusCountRow> rows =
                springDataRepository.countByPartyGroupedByStatus(accountId, projectId);

        Map<ContractTab, Long> result = new EnumMap<>(ContractTab.class);
        for (ContractTab tab : ContractTab.values()) {
            long count = rows.stream()
                    .filter(row -> matches(tab, row))
                    .mapToLong(ContractStatusCountRow::count)
                    .sum();
            // 0인 탭도 키로 넣는다. 화면이 탭을 전부 그려야 한다.
            result.put(tab, count);
        }
        return result;
    }

    /** 목록 쿼리의 {@code mySignatureStatus IS NULL OR ...} + {@code status IN :tabStatuses} 와 같다. */
    private boolean matches(ContractTab tab, ContractStatusCountRow row) {
        if (!tab.getStatuses().contains(row.status())) {
            return false;
        }
        return tab.getMySignatureStatus() == null
                || tab.getMySignatureStatus() == row.mySignatureStatus();
    }

    @Override
    public List<Contract> findByProjectId(Long projectId) {
        return springDataRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .map(contractMapper::toDomain)
                .toList();
    }

    @Override
    public long countSignedByPositionId(Long positionId) {
        return springDataRepository.countByPositionIdAndStatusIn(positionId, SIGNED_STATUSES);
    }
}
