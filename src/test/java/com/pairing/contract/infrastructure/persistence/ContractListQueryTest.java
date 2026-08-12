package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractTab;
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
import static org.assertj.core.api.Assertions.entry;

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

    /** 양측 서명까지 마친 계약. 체결되면 SIGNED 가 된다. */
    private Long concludedContract(Long positionId) {
        Contract contract = Contract.create(
                PROJECT_A * 10 + positionId, PROJECT_A, positionId, 1L, 2L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        contract.completeDraft("{}", null);
        contract.sign(CLIENT_ACCOUNT_ID, "SESSION", null, null, null, null);
        contract.sign(FREELANCER_ACCOUNT_ID, "SESSION", null, null, null, null);
        return contractRepository.save(contract).getId();
    }

    /** 아무도 서명하지 않은 서명 대기 계약. AI 문구가 채워져 SIGN_PENDING 이 된 상태다. */
    private Long signPendingContract(Long positionId) {
        return contractRepository.save(draft(positionId)).getId();
    }

    /** 클라이언트만 서명한 계약. 상태는 여전히 SIGN_PENDING 이다. */
    private Long clientSignedContract(Long positionId) {
        Contract contract = draft(positionId);
        contract.sign(CLIENT_ACCOUNT_ID, "SESSION", null, null, null, null);
        return contractRepository.save(contract).getId();
    }

    /** 프리랜서만 서명한 계약. 역시 SIGN_PENDING 이다. */
    private Long freelancerSignedContract(Long positionId) {
        Contract contract = draft(positionId);
        contract.sign(FREELANCER_ACCOUNT_ID, "SESSION", null, null, null, null);
        return contractRepository.save(contract).getId();
    }

    private Contract draft(Long positionId) {
        Contract contract = Contract.create(
                PROJECT_A * 10 + positionId, PROJECT_A, positionId, 1L, 2L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        contract.completeDraft("{}", null);
        return contract;
    }

    @Test
    @DisplayName("서명 대기와 상대방 서명 대기가 갈린다 — 계약 상태는 둘 다 SIGN_PENDING 이다")
    void splitsPendingByMySignature() {
        Long notSignedYet = signPendingContract(1L);
        Long clientSigned = clientSignedContract(2L);

        // 클라이언트 기준: 내가 안 한 것 / 내가 하고 상대를 기다리는 것
        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_ME))
                .containsExactly(notSignedYet);
        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_COUNTERPART))
                .containsExactly(clientSigned);

        // 프리랜서가 보면 정확히 반대다. 같은 파라미터로 역할이 갈린다.
        assertThat(tab(FREELANCER_ACCOUNT_ID, ContractTab.AWAITING_ME))
                .containsExactlyInAnyOrder(notSignedYet, clientSigned);
        assertThat(tab(FREELANCER_ACCOUNT_ID, ContractTab.AWAITING_COUNTERPART)).isEmpty();
    }

    @Test
    @DisplayName("상대만 서명한 계약도 내 서명 대기에 뜬다 — 아무도 서명 안 한 것만 세면 안 된다")
    void countsContractsSignedOnlyByCounterpart() {
        // 판정 기준이 "내 서명이 PENDING" 이라야 한다. "서명이 하나도 없음" 으로 짜면
        // 프리랜서가 먼저 서명한 계약이 클라이언트의 할 일 목록에서 사라진다.
        Long nobodySigned = signPendingContract(1L);
        Long freelancerOnly = freelancerSignedContract(2L);

        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_ME))
                .containsExactlyInAnyOrder(nobodySigned, freelancerOnly);
        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_COUNTERPART)).isEmpty();

        // 같은 두 건을 프리랜서가 보면 하나는 이미 내가 서명한 것이다.
        assertThat(tab(FREELANCER_ACCOUNT_ID, ContractTab.AWAITING_ME))
                .containsExactly(nobodySigned);
        assertThat(tab(FREELANCER_ACCOUNT_ID, ContractTab.AWAITING_COUNTERPART))
                .containsExactly(freelancerOnly);
    }

    @Test
    @DisplayName("DRAFT 는 서명 대기 탭에 안 들어간다 — 아직 서명할 수 없는 상태다")
    void draftIsExcludedFromSignTabs() {
        // AI 가 문구를 채우는 2~5초 과도기다. 탭에 넣으면 눌러도 아무 일이 안 일어난다.
        Long draftContract = createContract(PROJECT_A, 1L);

        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_ME)).isEmpty();
        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_COUNTERPART)).isEmpty();
        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.CONCLUDED)).isEmpty();
        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.ALL)).containsExactly(draftContract);
    }

    @Test
    @DisplayName("체결 완료 탭은 서명이 끝난 계약만 담는다")
    void concludedTabHoldsSignedOnly() {
        createContract(PROJECT_A, 1L);
        Long concluded = concludedContract(2L);

        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.CONCLUDED)).containsExactly(concluded);
    }

    @Test
    @DisplayName("탭이 null 이면 전체와 같다")
    void nullTabMeansAll() {
        createContract(PROJECT_A, 1L);
        concludedContract(2L);

        assertThat(tab(CLIENT_ACCOUNT_ID, ContractTab.ALL)).hasSize(2);
        assertThat(tab(CLIENT_ACCOUNT_ID, null)).hasSize(2);
    }

    private List<Long> tab(Long accountId, ContractTab tab) {
        return contractRepository.findByParty(accountId, null, null, tab, PageRequest.of(0, 10))
                .getContent().stream().map(Contract::getId).toList();
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
    @DisplayName("계약별로 결제할 정산 ID 를 준다 — 낸 건과 남의 건은 빠진다")
    void findsPayableSettlementIdsByContract() {
        Long unpaid = createContract(PROJECT_A, 1L);
        Long paid = createContract(PROJECT_A, 2L);

        Long unpaidSettlementId = settlementRepository.save(freelancerDeposit(unpaid)).getId();
        payFor(settlementRepository.save(freelancerDeposit(paid)));

        // 프리랜서가 보면 아직 안 낸 건만 결제 대상이다.
        assertThat(settlementRepository.findPayableSettlementIdsByContract(
                FREELANCER_ACCOUNT_ID, Set.of(unpaid, paid)))
                .containsExactly(entry(unpaid, unpaidSettlementId));

        // 계약에 걸린 정산은 전부 프리랜서 몫이라 클라이언트에게는 결제할 게 없다.
        assertThat(settlementRepository.findPayableSettlementIdsByContract(
                CLIENT_ACCOUNT_ID, Set.of(unpaid, paid))).isEmpty();
    }

    @Test
    @DisplayName("빈 입력이면 결제 대상 조회도 쿼리를 타지 않는다")
    void emptyInputSkipsPayableQuery() {
        assertThat(settlementRepository.findPayableSettlementIdsByContract(
                FREELANCER_ACCOUNT_ID, List.of())).isEmpty();
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
