package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractTab;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 탭 배지 건수.
 *
 * <p>배지가 목록과 <b>같은 규칙</b>으로 세는지가 핵심이다. 조건을 옮겨 적으면 갈라지는데,
 * 특히 "서명 대기"와 "상대방 서명 대기"는 <b>계약 상태가 둘 다 {@code SIGN_PENDING}</b> 이라
 * 계약 상태만으로 세면 두 탭이 같은 숫자가 된다. 화면에는 그럴듯해 보여서 눈으로는 안 잡힌다.
 *
 * <p>그래서 매 검증마다 목록 API 의 {@code totalElements} 와 맞춰 본다. 한쪽만 고치면 깨진다.
 */
@SpringBootTest
@Transactional
class ContractTabCountTest {

    private static final Long CLIENT_ACCOUNT_ID = 970_001L;
    private static final Long FREELANCER_ACCOUNT_ID = 970_002L;
    private static final Long PROJECT_ID = 9_700L;
    private static final Long OTHER_PROJECT_ID = 9_701L;

    @Autowired
    private ContractRepository contractRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("건수가 0인 탭도 키로 내려간다")
    void includesZeroTabs() {
        // 화면이 탭을 전부 그려야 한다. 키가 빠지면 배지가 사라지는 게 아니라 undefined 가 찍힌다.
        Map<ContractTab, Long> counts = contractRepository.countMyTabs(CLIENT_ACCOUNT_ID, null);

        assertThat(counts).containsOnlyKeys(ContractTab.values());
    }

    @Test
    @DisplayName("서명 전 계약은 양측 모두 '서명 대기'로 잡힌다")
    void countsAwaitingMeForBothParties() {
        signPending(9_711L);

        assertThat(tabCount(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_ME)).isEqualTo(1);
        assertThat(tabCount(FREELANCER_ACCOUNT_ID, ContractTab.AWAITING_ME)).isEqualTo(1);
    }

    @Test
    @DisplayName("한쪽이 서명하면 그 사람만 '상대방 서명 대기'로 옮겨간다")
    void movesToAwaitingCounterpartForSignerOnly() {
        // 계약 상태는 여전히 SIGN_PENDING 이다. 상태로 세면 두 사람이 같은 탭에 잡힌다.
        Contract contract = signPending(9_712L);
        contract.sign(FREELANCER_ACCOUNT_ID, "SESSION", null, null, null, null);
        contractRepository.updateState(contract);
        flushAndClear();

        assertThat(tabCount(FREELANCER_ACCOUNT_ID, ContractTab.AWAITING_ME)).isZero();
        assertThat(tabCount(FREELANCER_ACCOUNT_ID, ContractTab.AWAITING_COUNTERPART)).isEqualTo(1);

        // 아직 서명하지 않은 클라이언트는 그대로 '서명 대기' 다.
        assertThat(tabCount(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_ME)).isEqualTo(1);
        assertThat(tabCount(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_COUNTERPART)).isZero();
    }

    @Test
    @DisplayName("배지 건수가 목록의 totalElements 와 같다")
    void matchesListTotal() {
        signPending(9_713L);
        Contract signed = signPending(9_714L);
        signed.sign(CLIENT_ACCOUNT_ID, "SESSION", null, null, null, null);
        contractRepository.updateState(signed);
        flushAndClear();

        for (ContractTab tab : ContractTab.values()) {
            long badge = tabCount(CLIENT_ACCOUNT_ID, tab);
            long list = contractRepository
                    .findByParty(CLIENT_ACCOUNT_ID, null, null, tab, PageRequest.of(0, 100))
                    .getTotalElements();

            assertThat(badge).as("%s 탭", tab).isEqualTo(list);
        }
    }

    @Test
    @DisplayName("DRAFT 는 ALL 에만 잡힌다")
    void countsDraftOnlyInAll() {
        // AI 가 문구를 채우는 2~5초짜리 과도기라 서명할 수 없다. 서명 대기 탭에 넣으면 눌러도 안 된다.
        Contract draft = Contract.create(9_715L, PROJECT_ID, 10L, 100L, 200L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        contractRepository.save(draft);
        flushAndClear();

        assertThat(tabCount(CLIENT_ACCOUNT_ID, ContractTab.ALL)).isEqualTo(1);
        assertThat(tabCount(CLIENT_ACCOUNT_ID, ContractTab.AWAITING_ME)).isZero();
    }

    @Test
    @DisplayName("남의 계약은 세지 않는다")
    void ignoresOtherParties() {
        signPending(9_716L);

        assertThat(tabCount(999_999L, ContractTab.ALL)).isZero();
    }

    @Test
    @DisplayName("projectId 를 주면 그 프로젝트만 센다")
    void filtersByProject() {
        signPending(9_717L);
        signPendingOnProject(9_718L, OTHER_PROJECT_ID);

        assertThat(tabCount(CLIENT_ACCOUNT_ID, ContractTab.ALL)).isEqualTo(2);

        Map<ContractTab, Long> scoped = contractRepository.countMyTabs(CLIENT_ACCOUNT_ID, PROJECT_ID);
        assertThat(scoped.get(ContractTab.ALL)).isEqualTo(1);
    }

    private long tabCount(Long accountId, ContractTab tab) {
        return contractRepository.countMyTabs(accountId, null).getOrDefault(tab, 0L);
    }

    private Contract signPending(Long negotiationId) {
        return signPendingOnProject(negotiationId, PROJECT_ID);
    }

    /** negotiationId 는 UNIQUE 라 테스트끼리 겹치지 않게 받는다. */
    private Contract signPendingOnProject(Long negotiationId, Long projectId) {
        Contract contract = Contract.create(negotiationId, projectId, 10L, 100L, 200L,
                CLIENT_ACCOUNT_ID, FREELANCER_ACCOUNT_ID, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
        contract.completeDraft("{}", null);

        Contract saved = contractRepository.save(contract);
        flushAndClear();
        return contractRepository.findById(saved.getId()).orElseThrow();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
