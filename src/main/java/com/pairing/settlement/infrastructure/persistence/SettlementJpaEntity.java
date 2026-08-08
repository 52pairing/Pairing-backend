package com.pairing.settlement.infrastructure.persistence;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * settlement 테이블 매핑.
 *
 * <p>ledger_entry_id 는 매핑하지 않는다. 원장 도메인이 아직 없고 nullable 이라 INSERT 를 막지 않는다.
 *
 * <p>created_at 은 DB 기본값을 쓰지 않고 도메인이 정한 값을 넣는다. 응답의 정렬 기준이라
 * 애플리케이션 시각과 어긋나면 곤란하다.
 */
@Entity
@Table(name = "settlement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "settlement_no", nullable = false, length = 50)
    private String settlementNo;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "contract_id")
    private Long contractId;

    @Column(name = "payer_account_id", nullable = false)
    private Long payerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_role", nullable = false, length = 20)
    private PartyRole payerRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 20)
    private SettlementPhase phase;

    @Column(name = "base_amount", nullable = false)
    private Long baseAmount;

    @Column(name = "fee_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal feeRate;

    @Column(name = "grade_discount", nullable = false, precision = 5, scale = 2)
    private BigDecimal gradeDiscount;

    @Column(name = "fee_amount", nullable = false)
    private Long feeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SettlementStatus status;

    @Column(name = "payment_method_id")
    private Long paymentMethodId;

    @Column(name = "approval_no", length = 50)
    private String approvalNo;

    @Column(name = "fail_reason", length = 255)
    private String failReason;

    @Column(name = "overdue_reason", length = 255)
    private String overdueReason;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public SettlementJpaEntity(Long id, String settlementNo, Long projectId, Long contractId,
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
}
