package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 계약 목록 쿼리를 실제 DB 에 태운다.
 *
 * <p>{@code :projectId IS NULL} 같은 선택 조건은 <b>파싱이 아니라 실행</b> 시점에 깨진다.
 * 컨텍스트가 떴다는 것만으로는 검증이 안 돼서 두 경우를 모두 돌려 본다.
 */
@SpringBootTest
@Transactional
class ContractListQueryTest {

    private static final Long CLIENT_ACCOUNT_ID = 940_001L;
    private static final Long FREELANCER_ACCOUNT_ID = 940_002L;
    private static final Long PROJECT_A = 940_101L;
    private static final Long PROJECT_B = 940_102L;

    @Autowired
    private ContractRepository contractRepository;
    @Autowired
    private SettlementRepository settlementRepository;

    private Long createContract(Long projectId, Long positionId) {
        return contractRepository.save(Contract.create(
                projectId * 10 + positionId, projectId, positionId, 1L, 2L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null)).getId();
    }

    @Test
    @DisplayName("projectId 를 주면 그 프로젝트 계약만, 안 주면 전부 나온다")
    void filtersByProjectId() {
        createContract(PROJECT_A, 1L);
        createContract(PROJECT_A, 2L);
        createContract(PROJECT_B, 3L);

        List<Contract> all = contractRepository
                .findByParty(CLIENT_ACCOUNT_ID, null, null, PageRequest.of(0, 10)).getContent();
        List<Contract> onlyA = contractRepository
                .findByParty(CLIENT_ACCOUNT_ID, PROJECT_A, null, PageRequest.of(0, 10)).getContent();

        assertThat(all).hasSize(3);
        assertThat(onlyA).hasSize(2)
                .allSatisfy(contract -> assertThat(contract.getProjectId()).isEqualTo(PROJECT_A));
    }

    @Test
    @DisplayName("최신 계약이 앞에 온다")
    void ordersByNewest() {
        Long first = createContract(PROJECT_A, 1L);
        Long second = createContract(PROJECT_A, 2L);

        List<Contract> found = contractRepository
                .findByParty(CLIENT_ACCOUNT_ID, PROJECT_A, null, PageRequest.of(0, 10)).getContent();

        assertThat(found).extracting(Contract::getId).containsExactly(second, first);
    }

    @Test
    @DisplayName("당사자가 아니면 안 나온다")
    void hidesOtherPartyContracts() {
        createContract(PROJECT_A, 1L);

        assertThat(contractRepository.findByParty(999_999L, null, null, PageRequest.of(0, 10)))
                .isEmpty();
    }

    @Test
    @DisplayName("빈 입력은 쿼리를 타지 않는다")
    void emptyInputSkipsQuery() {
        // 빈 목록을 그대로 넘기면 IN () 이 되어 DB 가 거부한다. 어댑터가 막는지 본다.
        assertThat(settlementRepository.findPaidFreelancerDepositContractIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("결제한 프리랜서 착수금만 골라낸다")
    void findsOnlyPaidFreelancerDeposits() {
        Long paid = createContract(PROJECT_A, 1L);
        Long unpaid = createContract(PROJECT_A, 2L);

        payFor(settlementRepository.save(freelancerDeposit(paid)));
        settlementRepository.save(freelancerDeposit(unpaid));

        assertThat(settlementRepository.findPaidFreelancerDepositContractIds(Set.of(paid, unpaid)))
                .containsExactly(paid);
    }

    @Test
    @DisplayName("클라이언트 착수금은 프리랜서 결제로 세지 않는다")
    void ignoresClientDeposit() {
        // 같은 프로젝트에 갑·을 착수금이 함께 붙는다. 역할을 안 걸면 남의 결제로 배지가 꺼진다.
        Long contractId = createContract(PROJECT_A, 1L);
        payFor(settlementRepository.save(Settlement.createClientDeposit(
                PROJECT_A, CLIENT_ACCOUNT_ID, 20_000_000L,
                new BigDecimal("3.00"), BigDecimal.ZERO, 600_000L)));

        assertThat(settlementRepository.findPaidFreelancerDepositContractIds(Set.of(contractId)))
                .isEmpty();
    }

    @Test
    @DisplayName("정산 목록도 projectId 로 거를 수 있다")
    void filtersSettlementsByProjectId() {
        settlementRepository.save(freelancerDeposit(createContract(PROJECT_A, 1L)));
        settlementRepository.save(Settlement.createFreelancerDeposit(
                PROJECT_B, createContract(PROJECT_B, 2L), FREELANCER_ACCOUNT_ID, 10_000_000L,
                new BigDecimal("4.00"), BigDecimal.ZERO, 400_000L));

        assertThat(settlementRepository.findByPayer(
                FREELANCER_ACCOUNT_ID, null, null, null, PageRequest.of(0, 10))).hasSize(2);
        assertThat(settlementRepository.findByPayer(
                FREELANCER_ACCOUNT_ID, PROJECT_A, null, null, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(s -> assertThat(s.getProjectId()).isEqualTo(PROJECT_A));
    }

    private Settlement freelancerDeposit(Long contractId) {
        return Settlement.createFreelancerDeposit(
                PROJECT_A, contractId, FREELANCER_ACCOUNT_ID, 20_000_000L,
                new BigDecimal("4.00"), BigDecimal.ZERO, 800_000L);
    }

    /** {@code pay()} 는 승인번호에 id 를 써서 저장된 뒤에만 부를 수 있다. 실제 결제 흐름과 같다. */
    private void payFor(Settlement saved) {
        saved.pay(1L);
        settlementRepository.save(saved);
    }
}
