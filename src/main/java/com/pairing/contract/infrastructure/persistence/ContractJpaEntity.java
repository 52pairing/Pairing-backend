package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * contract 테이블 매핑.
 *
 * <p>{@code salary_amount} 는 스키마 원본에 없다가 나중에 추가한 컬럼이다.
 * {@code precision}/{@code scale} 을 명시해야 {@code ddl-auto: update} 가
 * {@code numeric(15,0)} 으로 만든다. 안 쓰면 {@code bigint} 가 되어 다른 DB 와 어긋난다.
 *
 * <p>서명은 계약 애그리거트의 자식이다. 항상 2건이라 페이징이 없어 컬렉션 fetch 문제는 없지만,
 * 목록 조회에서 N+1 이 나지 않도록 {@code @BatchSize} 를 둔다.
 *
 * <p>created_at / updated_at 은 DB 기본값과 트리거가 채운다.
 */
@Entity
@Table(name = "contract")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contract_no", length = 50)
    private String contractNo;

    @Column(name = "negotiation_id", nullable = false)
    private Long negotiationId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "position_id", nullable = false)
    private Long positionId;

    @Column(name = "client_id", nullable = false)
    private Long clientId;

    @Column(name = "freelancer_id", nullable = false)
    private Long freelancerId;

    @Column(name = "salary_amount", precision = 15, scale = 0)
    private Long salaryAmount;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 0)
    private Long totalAmount;

    @Column(name = "down_amount", nullable = false, precision = 15, scale = 0)
    private Long downAmount;

    @Column(name = "final_amount", nullable = false, precision = 15, scale = 0)
    private Long finalAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_style", nullable = false, length = 20)
    private WorkStyle workStyle;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_form", nullable = false, length = 20)
    private WorkForm workForm;

    @Column(name = "work_location", length = 255)
    private String workLocation;

    @Column(name = "inspection_days", nullable = false)
    private int inspectionDays;

    @Column(name = "payment_days", nullable = false)
    private int paymentDays;

    @Column(name = "confidential_years", nullable = false)
    private int confidentialYears;

    @Column(name = "penalty_rate", nullable = false, precision = 5, scale = 2)
    private BigDecimal penaltyRate;

    @Column(name = "special_terms", columnDefinition = "TEXT")
    private String specialTerms;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_json")
    private String contentJson;

    @Column(name = "pdf_file_id")
    private Long pdfFileId;

    /**
     * 체결 시점 정산 계좌(암호문). 계약서 제5조에 찍힌 한 줄을 굳혀 둔다.
     *
     * <p>{@code payment_method.account_no_enc} 와 같은 기준으로 평문을 두지 않는다.
     * 체결 전 계약과 이 칸이 생기기 전 계약은 비어 있고, 그때는 현재 계좌를 읽는다.
     */
    @Column(name = "settlement_account_enc")
    private byte[] settlementAccountEnc;

    @Column(name = "esign_provider", length = 30)
    private String esignProvider;

    @Column(name = "esign_doc_id", length = 255)
    private String esignDocId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ContractStatus status;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "terminated_by", length = 20)
    private PartyRole terminatedBy;

    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "contract", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100)
    private List<ContractSignatureJpaEntity> signatures = new ArrayList<>();

    public ContractJpaEntity(Long id, String contractNo, Long negotiationId, Long projectId,
                             Long positionId, Long clientId, Long freelancerId,
                             Long salaryAmount, Long totalAmount, Long downAmount, Long finalAmount,
                             LocalDate startDate, LocalDate endDate,
                             WorkStyle workStyle, WorkForm workForm, String workLocation,
                             int inspectionDays, int paymentDays, int confidentialYears,
                             BigDecimal penaltyRate, String specialTerms, String contentJson,
                             Long pdfFileId, byte[] settlementAccountEnc,
                             String esignProvider, String esignDocId,
                             ContractStatus status, LocalDateTime signedAt, LocalDateTime completedAt,
                             LocalDateTime terminatedAt, PartyRole terminatedBy,
                             LocalDate retentionUntil, LocalDateTime createdAt) {
        this.id = id;
        this.contractNo = contractNo;
        this.negotiationId = negotiationId;
        this.projectId = projectId;
        this.positionId = positionId;
        this.clientId = clientId;
        this.freelancerId = freelancerId;
        this.salaryAmount = salaryAmount;
        this.totalAmount = totalAmount;
        this.downAmount = downAmount;
        this.finalAmount = finalAmount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.workStyle = workStyle;
        this.workForm = workForm;
        this.workLocation = workLocation;
        this.inspectionDays = inspectionDays;
        this.paymentDays = paymentDays;
        this.confidentialYears = confidentialYears;
        this.penaltyRate = penaltyRate;
        this.specialTerms = specialTerms;
        this.contentJson = contentJson;
        this.pdfFileId = pdfFileId;
        this.settlementAccountEnc = settlementAccountEnc;
        this.esignProvider = esignProvider;
        this.esignDocId = esignDocId;
        this.status = status;
        this.signedAt = signedAt;
        this.completedAt = completedAt;
        this.terminatedAt = terminatedAt;
        this.terminatedBy = terminatedBy;
        this.retentionUntil = retentionUntil;
        this.createdAt = createdAt;
    }

    public void addSignature(ContractSignatureJpaEntity signature) {
        this.signatures.add(signature);
        signature.assignContract(this);
    }

    /** 채번한 계약 번호를 반영한다. INSERT 뒤 같은 트랜잭션에서 한 번만 호출한다. */
    public void applyContractNo(String contractNo) {
        this.contractNo = contractNo;
    }

    /**
     * 상태 전이 결과만 반영한다. 서명은 {@link ContractSignatureJpaEntity#applyState} 로 따로 맞춘다.
     *
     * <p>매퍼로 새 그래프를 만들어 save 하면 자식이 다시 INSERT 되어
     * {@code uk_contract_signature} 에 걸린다. 상태만 바뀌는 경로는 영속 엔티티에 이걸 쓴다.
     */
    public void applyState(ContractJpaEntity source) {
        this.salaryAmount = source.salaryAmount;
        this.totalAmount = source.totalAmount;
        this.downAmount = source.downAmount;
        this.finalAmount = source.finalAmount;
        this.startDate = source.startDate;
        this.endDate = source.endDate;
        this.workStyle = source.workStyle;
        this.workForm = source.workForm;
        this.workLocation = source.workLocation;
        this.specialTerms = source.specialTerms;
        this.contentJson = source.contentJson;
        this.pdfFileId = source.pdfFileId;
        this.settlementAccountEnc = source.settlementAccountEnc;
        this.esignProvider = source.esignProvider;
        this.esignDocId = source.esignDocId;
        this.status = source.status;
        this.signedAt = source.signedAt;
        this.completedAt = source.completedAt;
        this.terminatedAt = source.terminatedAt;
        this.terminatedBy = source.terminatedBy;
        this.retentionUntil = source.retentionUntil;
    }
}
