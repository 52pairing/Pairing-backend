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

    /** 결제 버튼 활성화 기준. 실패한 건은 다시 시도할 수 있다. (P32) */
    public boolean isPayable() {
        return this.status == SettlementStatus.PENDING
                || this.status == SettlementStatus.OVERDUE
                || this.status == SettlementStatus.FAILED;
    }

    public boolean isPayableBy(Long accountId) {
        return this.payerAccountId.equals(accountId);
    }

    /** 착수금 결제는 모집 시작으로 이어진다. 성공보수는 프로젝트 상태를 바꾸지 않는다. (P27) */
    public boolean startsRecruiting() {
        return this.phase == SettlementPhase.DEPOSIT && this.payerRole == PartyRole.CLIENT;
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
