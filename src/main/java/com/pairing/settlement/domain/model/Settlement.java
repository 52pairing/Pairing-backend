package com.pairing.settlement.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.exception.SettlementErrorCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 수수료 정산 1건. (요구사항 R34 / 정책 P27·P29·P32)
 *
 * <p>실제 용역비는 플랫폼을 거치지 않는다. 여기서 다루는 돈은 플랫폼 수수료뿐이다.
 *
 * <p>납부 기한은 두지 않는다. 착수금은 내지 않으면 모집이 시작되지 않을 뿐 강제성이 없고,
 * 정책에도 기준값이 없다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement {

    /** 채번 전 임시 정산번호. settlement_no 가 NOT NULL + UNIQUE 라 INSERT 시점에 값이 있어야 한다. */
    private static final String TEMP_NO_PREFIX = "TMP-";

    private Long id;
    private String settlementNo;
    private Long projectId;
    private Long contractId;
    private Long payerAccountId;
    private PartyRole payerRole;
    private SettlementPhase phase;

    private Long baseAmount;
    private BigDecimal feeRate;
    private BigDecimal gradeDiscount;
    private Long feeAmount;

    private SettlementStatus status;
    private Long paymentMethodId;
    private String approvalNo;
    private String failReason;
    private String overdueReason;
    private LocalDate dueDate;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    private Settlement(Long id, String settlementNo, Long projectId, Long contractId,
                       Long payerAccountId, PartyRole payerRole, SettlementPhase phase,
                       Long baseAmount, BigDecimal feeRate, BigDecimal gradeDiscount, Long feeAmount,
                       SettlementStatus status, Long paymentMethodId, String approvalNo,
                       String failReason, String overdueReason, LocalDate dueDate,
                       LocalDateTime paidAt, LocalDateTime createdAt) {
        this.id = id;
        this.settlementNo = settlementNo;
        this.projectId = projectId;
        this.contractId = contractId;
        this.payerAccountId = payerAccountId;
        this.payerRole = payerRole;
        this.phase = phase;
        this.baseAmount = baseAmount;
        this.feeRate = feeRate;
        this.gradeDiscount = gradeDiscount;
        this.feeAmount = feeAmount;
        this.status = status;
        this.paymentMethodId = paymentMethodId;
        this.approvalNo = approvalNo;
        this.failReason = failReason;
        this.overdueReason = overdueReason;
        this.dueDate = dueDate;
        this.paidAt = paidAt;
        this.createdAt = createdAt;
    }

    /**
     * 클라이언트 착수금. 프로젝트 등록 완료 시점에 만들어진다. (P27)
     *
     * <p>정산번호는 임시값으로 시작한다. 최종 번호가 id 를 포함하는데 저장 전에는 id 가 없다.
     * 저장 직후 {@link #assignNo} 로 덮어쓴다.
     */
    public static Settlement createClientDeposit(Long projectId, Long payerAccountId, long budgetAmount,
                                                 BigDecimal feeRate, BigDecimal gradeDiscount, long feeAmount) {
        return new Settlement(
                null,
                TEMP_NO_PREFIX + UUID.randomUUID(),
                projectId,
                null,
                payerAccountId,
                PartyRole.CLIENT,
                SettlementPhase.DEPOSIT,
                budgetAmount,
                feeRate,
                gradeDiscount,
                feeAmount,
                SettlementStatus.PENDING,
                null, null, null, null,
                null,
                null,
                LocalDateTime.now());
    }

    /**
     * 프리랜서 착수금. 계약 체결(양측 서명 완료) 시점에 만들어진다. (P27·P29)
     *
     * <p>기준 금액이 프로젝트 예산이 아니라 <b>그 계약의 총액</b>이다. 여러 명을 뽑는 프로젝트는
     * 프리랜서마다 계약 금액이 다르다. 클라이언트 착수금과 달리 {@code contractId} 가 채워진다.
     */
    public static Settlement createFreelancerDeposit(Long projectId, Long contractId, Long payerAccountId,
                                                     long contractAmount, BigDecimal feeRate,
                                                     BigDecimal gradeDiscount, long feeAmount) {
        return new Settlement(
                null,
                TEMP_NO_PREFIX + UUID.randomUUID(),
                projectId,
                contractId,
                payerAccountId,
                PartyRole.FREELANCER,
                SettlementPhase.DEPOSIT,
                contractAmount,
                feeRate,
                gradeDiscount,
                feeAmount,
                SettlementStatus.PENDING,
                null, null, null, null,
                null,
                null,
                LocalDateTime.now());
    }

    /**
     * 프리랜서 성공보수. 프로젝트가 완료 대기로 넘어간 시점에 만들어진다. (P30)
     *
     * <p>기준 금액은 착수금과 같은 <b>그 계약의 총액</b>이다. 클라이언트 성공보수가 프로젝트 예산을
     * 쓰는 것과 다르다. 요율도 금액 구간과 무관한 6% 단일이다.
     *
     * <p>계약 1건당 착수금·성공보수 두 건이 붙으므로 {@code contractId} 만으로는 찾을 수 없다.
     * 조회는 {@code phase} 를 함께 걸어야 한다.
     */
    public static Settlement createFreelancerSuccessFee(Long projectId, Long contractId,
                                                        Long payerAccountId, long contractAmount,
                                                        BigDecimal feeRate, BigDecimal gradeDiscount,
                                                        long feeAmount) {
        return new Settlement(
                null,
                TEMP_NO_PREFIX + UUID.randomUUID(),
                projectId,
                contractId,
                payerAccountId,
                PartyRole.FREELANCER,
                SettlementPhase.SUCCESS_FEE,
                contractAmount,
                feeRate,
                gradeDiscount,
                feeAmount,
                SettlementStatus.PENDING,
                null, null, null, null,
                null,
                null,
                LocalDateTime.now());
    }

    /**
     * 클라이언트 성공보수. 프로젝트가 완료 대기로 넘어간 시점에 만들어진다. (P30)
     *
     * <p>계약 도메인이 붙기 전까지 기준 금액은 프로젝트 예산이다. contractId 도 아직 없다.
     */
    public static Settlement createClientSuccessFee(Long projectId, Long payerAccountId, long baseAmount,
                                                    BigDecimal feeRate, BigDecimal gradeDiscount,
                                                    long feeAmount) {
        return new Settlement(
                null,
                TEMP_NO_PREFIX + UUID.randomUUID(),
                projectId,
                null,
                payerAccountId,
                PartyRole.CLIENT,
                SettlementPhase.SUCCESS_FEE,
                baseAmount,
                feeRate,
                gradeDiscount,
                feeAmount,
                SettlementStatus.PENDING,
                null, null, null, null,
                null,
                null,
                LocalDateTime.now());
    }

    public static Settlement reconstitute(Long id, String settlementNo, Long projectId, Long contractId,
                                          Long payerAccountId, PartyRole payerRole, SettlementPhase phase,
                                          Long baseAmount, BigDecimal feeRate, BigDecimal gradeDiscount,
                                          Long feeAmount, SettlementStatus status, Long paymentMethodId,
                                          String approvalNo, String failReason, String overdueReason,
                                          LocalDate dueDate, LocalDateTime paidAt, LocalDateTime createdAt) {
        return new Settlement(id, settlementNo, projectId, contractId, payerAccountId, payerRole, phase,
                baseAmount, feeRate, gradeDiscount, feeAmount, status, paymentMethodId, approvalNo,
                failReason, overdueReason, dueDate, paidAt, createdAt);
    }

    /** 채번된 id 로 최종 정산번호를 확정한다. 이미 확정됐으면 아무것도 하지 않는다. */
    public void assignNo(int year) {
        if (this.settlementNo != null && !this.settlementNo.startsWith(TEMP_NO_PREFIX)) {
            return;
        }
        this.settlementNo = "ST-%d-%06d".formatted(year, this.id);
    }

    /**
     * 기준 금액과 수수료를 다시 계산한 값으로 바꾼다.
     *
     * <p>프로젝트 예산이 바뀌면 요율 구간도 달라질 수 있어 셋을 함께 갱신한다.
     * 이미 결제된 건에는 쓰지 않는다. 낸 금액과 어긋나기 때문이다.
     */
    public void reprice(long baseAmount, BigDecimal feeRate, BigDecimal gradeDiscount, long feeAmount) {
        if (!isPayable()) {
            throw new BusinessException(SettlementErrorCode.NOT_PAYABLE);
        }
        this.baseAmount = baseAmount;
        this.feeRate = feeRate;
        this.gradeDiscount = gradeDiscount;
        this.feeAmount = feeAmount;
    }

    /**
     * 프로젝트가 등록 취소돼 낼 이유가 사라졌다. 결제 대상에서 뺀다.
     *
     * <p>이미 결제된 건은 건드리지 않는다. 낸 돈을 되돌리는 건 환불이라 별개다.
     * 등록 취소는 착수금 결제 전에만 가능해 실제로는 PENDING 만 대상이 된다.
     */
    public void cancel() {
        if (!isPayable()) {
            return;
        }
        this.status = SettlementStatus.CANCELED;
    }

    /** 결제 버튼 활성화 기준. 실패한 건은 다시 시도할 수 있다. (P32) */
    public boolean isPayable() {
        return this.status == SettlementStatus.PENDING
                || this.status == SettlementStatus.OVERDUE
                || this.status == SettlementStatus.FAILED;
    }

    public boolean isPayableBy(Long accountId) {
        return this.payerAccountId.equals(accountId);
    }

    /** 클라이언트 착수금 결제가 곧 모집 시작이다. (P27) */
    public boolean startsRecruiting() {
        return this.phase == SettlementPhase.DEPOSIT && this.payerRole == PartyRole.CLIENT;
    }

    /** 클라이언트 성공보수 결제가 곧 프로젝트 종료다. 프리랜서 성공보수는 상태를 바꾸지 않는다. (P30) */
    public boolean closesProject() {
        return this.phase == SettlementPhase.SUCCESS_FEE && this.payerRole == PartyRole.CLIENT;
    }

    /**
     * 프리랜서 착수금 결제는 프로젝트를 진행중으로 넘길 후보다. (P27)
     *
     * <p>이 한 건만으로는 부족하다. 같은 프로젝트의 다른 프리랜서가 아직 안 냈을 수 있고 인원이
     * 덜 찼을 수도 있다. 최종 판정은 호출부가 한다.
     */
    public boolean mayStartProgress() {
        return this.phase == SettlementPhase.DEPOSIT && this.payerRole == PartyRole.FREELANCER;
    }

    /**
     * 결제 완료 처리.
     *
     * <p>PG 연동이 없어 승인번호는 모의로 만든다. 실제 승인 응답으로 교체할 자리다.
     */
    public void pay(Long paymentMethodId) {
        if (!isPayable()) {
            throw new BusinessException(SettlementErrorCode.NOT_PAYABLE);
        }
        this.paymentMethodId = paymentMethodId;
        this.status = SettlementStatus.PAID;
        this.paidAt = LocalDateTime.now();
        this.approvalNo = "AP-%s-%04d".formatted(
                this.paidAt.toLocalDate().toString().replace("-", ""), this.id % 10_000);
        this.failReason = null;
        this.overdueReason = null;
    }
}
