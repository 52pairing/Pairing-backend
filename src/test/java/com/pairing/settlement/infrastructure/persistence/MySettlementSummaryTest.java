package com.pairing.settlement.infrastructure.persistence;

import com.pairing.settlement.application.result.MySettlementSummary;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.repository.SettlementRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 마이페이지 결제 내역 요약.
 *
 * <p>목록으로는 만들 수 없는 값이라 별도 쿼리를 쓴다. 화면이 그 숫자를 그대로 믿으므로
 * <b>무엇을 세고 무엇을 빼는지</b>가 전부다. 특히 셋을 본다.
 *
 * <ul>
 *   <li>결제 완료만 센다 — 문구가 "총 납부 수수료"다</li>
 *   <li>프로젝트 수는 DISTINCT — 한 프로젝트에 계약이 여러 건이면 수수료도 여러 건이다</li>
 *   <li>남의 정산은 안 센다</li>
 * </ul>
 */
@SpringBootTest
@Transactional
class MySettlementSummaryTest {

    private static final Long PAYER = 980_001L;
    private static final Long OTHER_PAYER = 980_002L;
    private static final Long PROJECT_A = 9_801L;
    private static final Long PROJECT_B = 9_802L;

    private static final BigDecimal RATE = new BigDecimal("4.00");
    private static final BigDecimal NO_DISCOUNT = BigDecimal.ZERO;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("낸 게 없으면 전부 0 — null 이 아니다")
    void returnsZeroWhenNothingPaid() {
        // 신규 가입자가 대부분 이 경우다. 화면이 널 검사를 하게 만들 이유가 없다.
        MySettlementSummary summary = settlementRepository.sumPaidByPayer(999_999L);

        assertThat(summary.totalAmount()).isZero();
        assertThat(summary.successFeeProjectCount()).isZero();
    }

    @Test
    @DisplayName("단계별 합계와 총액이 맞는다")
    void sumsByPhase() {
        payDeposit(PROJECT_A, 1_000_000L);
        paySuccessFee(PROJECT_A, 3_000_000L);
        flushAndClear();

        MySettlementSummary summary = settlementRepository.sumPaidByPayer(PAYER);

        assertThat(summary.depositAmount()).isEqualTo(1_000_000L);
        assertThat(summary.successFeeAmount()).isEqualTo(3_000_000L);
        assertThat(summary.totalAmount()).isEqualTo(4_000_000L);
    }

    @Test
    @DisplayName("결제하지 않은 정산은 세지 않는다")
    void ignoresUnpaid() {
        // "총 납부 수수료" 에 아직 내지 않은 돈이 들어가면 안 된다.
        payDeposit(PROJECT_A, 1_000_000L);
        pendingDeposit(PROJECT_B, 9_999_999L);
        flushAndClear();

        assertThat(settlementRepository.sumPaidByPayer(PAYER).depositAmount()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("같은 프로젝트에서 여러 건을 내도 프로젝트 수는 1이다")
    void countsProjectsDistinctly() {
        // 한 프로젝트에 계약이 여러 건이면 그 사람 수수료도 여러 건이다.
        // 행 수로 세면 "완료 프로젝트 수" 가 부풀어 오른다.
        paySuccessFee(PROJECT_A, 700_000L);
        paySuccessFee(PROJECT_A, 44_000L);
        flushAndClear();

        MySettlementSummary summary = settlementRepository.sumPaidByPayer(PAYER);

        assertThat(summary.successFeeAmount()).isEqualTo(744_000L);
        assertThat(summary.successFeeProjectCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("프로젝트가 다르면 따로 센다")
    void countsDistinctProjects() {
        paySuccessFee(PROJECT_A, 700_000L);
        paySuccessFee(PROJECT_B, 300_000L);
        flushAndClear();

        assertThat(settlementRepository.sumPaidByPayer(PAYER).successFeeProjectCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("착수금만 낸 프로젝트는 완료 프로젝트 수에 안 들어간다")
    void separatesPhaseProjectCounts() {
        // 프리랜서 화면의 "완료 프로젝트 수" 는 성공보수 기준이다. 착수금은 시작 시점에 낸다.
        payDeposit(PROJECT_A, 1_000_000L);
        paySuccessFee(PROJECT_B, 744_000L);
        flushAndClear();

        MySettlementSummary summary = settlementRepository.sumPaidByPayer(PAYER);

        assertThat(summary.depositProjectCount()).isEqualTo(1);
        assertThat(summary.successFeeProjectCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("남의 정산은 세지 않는다")
    void ignoresOtherPayers() {
        payDeposit(PROJECT_A, 1_000_000L);
        paid(Settlement.createClientDeposit(PROJECT_B, OTHER_PAYER, 50_000_000L,
                RATE, NO_DISCOUNT, 5_000_000L));
        flushAndClear();

        assertThat(settlementRepository.sumPaidByPayer(PAYER).totalAmount()).isEqualTo(1_000_000L);
    }

    private void payDeposit(Long projectId, long feeAmount) {
        settlementRepository.save(paid(deposit(projectId, feeAmount)));
    }

    private void pendingDeposit(Long projectId, long feeAmount) {
        settlementRepository.save(deposit(projectId, feeAmount));
    }

    private void paySuccessFee(Long projectId, long feeAmount) {
        settlementRepository.save(paid(successFee(projectId, feeAmount)));
    }

    /** contractId 는 (계약, 단계) 로 중복을 막으므로 정산마다 다르게 준다. */
    private Settlement deposit(Long projectId, long feeAmount) {
        return Settlement.createFreelancerDeposit(projectId, nextContractId(), PAYER,
                feeAmount * 25, RATE, NO_DISCOUNT, feeAmount);
    }

    private Settlement successFee(Long projectId, long feeAmount) {
        return Settlement.createFreelancerSuccessFee(projectId, nextContractId(), PAYER,
                feeAmount * 25, RATE, NO_DISCOUNT, feeAmount);
    }

    private long contractSeq = 98_000L;

    private Long nextContractId() {
        return ++contractSeq;
    }

    private Settlement paid(Settlement settlement) {
        Settlement saved = settlementRepository.save(settlement);
        saved.pay(1L);
        return saved;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
