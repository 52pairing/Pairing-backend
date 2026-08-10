package com.pairing.contract.domain.model;

import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.global.exception.BusinessException;
import com.pairing.meta.domain.model.PartyRole;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 표준계약서. 서명(ContractSignature)을 포함하는 애그리거트 루트다.
 *
 * <p>협상 1건당 계약 1건이다({@code uk_contract_negotiation}). 포지션에 여러 명을 뽑으면
 * 사람 수만큼 계약이 생긴다.
 *
 * <p><b>금액</b>은 월 단가({@code salaryAmount})가 원본이고 총액은 계약 개월 수를 곱해 만든다.
 * 협상이 합의해서 넘겨주는 값이 월 단가 하나뿐이라, 총액에서 역산하면 나눠떨어지지 않을 때
 * 원 단위가 어긋난다.
 *
 * <p>{@code downAmount}/{@code finalAmount}(착수금·잔금 분할)는 쓰지 않는다. 월 단가 지급이라
 * 나눌 원본이 없어 0 으로 채운다. 컬럼이 {@code NOT NULL} 이라 자리만 지킨다.
 * 플랫폼 수수료(착수금 수수료·성공보수)는 정산 도메인이 따로 계산하며 이것과 무관하다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Contract {

    private static final int DEFAULT_INSPECTION_DAYS = 7;
    private static final int DEFAULT_PAYMENT_DAYS = 7;
    private static final int DEFAULT_CONFIDENTIAL_YEARS = 3;
    private static final BigDecimal DEFAULT_PENALTY_RATE = new BigDecimal("10.00");

    /** 총액 분할을 쓰지 않는다는 표시. 컬럼이 NOT NULL 이라 0 을 넣는다. */
    private static final long NO_SPLIT = 0L;

    private static final int MAX_SPECIAL_TERMS = 5000;
    private static final int PARTY_COUNT = 2;

    private Long id;
    private String contractNo;

    private Long negotiationId;
    private Long projectId;
    private Long positionId;
    private Long clientId;
    private Long freelancerId;

    /** 월 용역대금(원). 협상 AMOUNT 타결값. */
    private Long salaryAmount;
    /** 총 계약 금액(원). salaryAmount x 계약 개월 수. */
    private Long totalAmount;
    private Long downAmount;
    private Long finalAmount;

    private LocalDate startDate;
    private LocalDate endDate;

    private WorkStyle workStyle;
    private WorkForm workForm;
    private String workLocation;

    private int inspectionDays;
    private int paymentDays;
    private int confidentialYears;
    private BigDecimal penaltyRate;

    private String specialTerms;
    private String contentJson;
    private Long pdfFileId;

    private String esignProvider;
    private String esignDocId;

    private ContractStatus status;
    private LocalDateTime signedAt;
    private LocalDateTime completedAt;
    private LocalDateTime terminatedAt;
    private PartyRole terminatedBy;
    private LocalDate retentionUntil;
    private LocalDateTime createdAt;

    private List<ContractSignature> signatures;

    private Contract(Long id, String contractNo, Long negotiationId, Long projectId, Long positionId,
                     Long clientId, Long freelancerId, Long salaryAmount, Long totalAmount,
                     Long downAmount, Long finalAmount, LocalDate startDate, LocalDate endDate,
                     WorkStyle workStyle, WorkForm workForm, String workLocation,
                     int inspectionDays, int paymentDays, int confidentialYears, BigDecimal penaltyRate,
                     String specialTerms, String contentJson, Long pdfFileId,
                     String esignProvider, String esignDocId, ContractStatus status,
                     LocalDateTime signedAt, LocalDateTime completedAt, LocalDateTime terminatedAt,
                     PartyRole terminatedBy, LocalDate retentionUntil, LocalDateTime createdAt,
                     List<ContractSignature> signatures) {
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
        this.esignProvider = esignProvider;
        this.esignDocId = esignDocId;
        this.status = status;
        this.signedAt = signedAt;
        this.completedAt = completedAt;
        this.terminatedAt = terminatedAt;
        this.terminatedBy = terminatedBy;
        this.retentionUntil = retentionUntil;
        this.createdAt = createdAt;
        this.signatures = signatures;
    }

    // ==========================================
    // 생성 · 복원
    // ==========================================

    /**
     * 협상 타결 직후 계약서를 만든다. 상태는 DRAFT 로 시작하고 갑·을 서명 2건이 함께 생긴다.
     *
     * <p>DRAFT 인 이유는 본문의 자유 텍스트(담당 업무·업무 범위·특약사항)가 아직 안 채워졌기
     * 때문이다. 그 작업은 AI 서버를 부르므로 협상 타결을 붙잡지 않도록 커밋 뒤로 미룬다.
     * 읽을 내용이 없는 계약서에 서명이 들어가면 안 되므로 {@link #completeDraft} 전까지 막는다.
     *
     * <p>계약 번호는 id 가 있어야 만들 수 있어 저장 후 {@link #assignContractNo} 로 채운다.
     * 검수·지급 기한과 위약금율은 정책 고정값이라 인자로 받지 않는다.
     *
     * @param months 계약 개월 수. 총액 계산에만 쓴다. 기간 단위가 주(week)면 호출부가 개월로 환산해 넘긴다.
     */
    public static Contract create(Long negotiationId, Long projectId, Long positionId,
                                  Long clientId, Long freelancerId,
                                  Long clientAccountId, Long freelancerAccountId,
                                  Long salaryAmount, int months,
                                  LocalDate startDate, LocalDate endDate,
                                  WorkStyle workStyle, WorkForm workForm, String workLocation,
                                  String specialTerms) {

        validate(negotiationId, projectId, positionId, clientId, freelancerId,
                salaryAmount, months, startDate, endDate, workStyle, workForm, specialTerms);

        List<ContractSignature> signatures = new ArrayList<>();
        signatures.add(ContractSignature.create(clientAccountId, PartyRole.CLIENT));
        signatures.add(ContractSignature.create(freelancerAccountId, PartyRole.FREELANCER));

        return new Contract(null, null, negotiationId, projectId, positionId, clientId, freelancerId,
                salaryAmount, salaryAmount * months, NO_SPLIT, NO_SPLIT,
                startDate, endDate, workStyle, workForm,
                workStyle == WorkStyle.ONSITE ? workLocation : null,
                DEFAULT_INSPECTION_DAYS, DEFAULT_PAYMENT_DAYS, DEFAULT_CONFIDENTIAL_YEARS,
                DEFAULT_PENALTY_RATE, specialTerms, null, null, null, null,
                ContractStatus.DRAFT, null, null, null, null, null,
                LocalDateTime.now(), signatures);
    }

    public static Contract reconstitute(Long id, String contractNo, Long negotiationId, Long projectId,
                                        Long positionId, Long clientId, Long freelancerId,
                                        Long salaryAmount, Long totalAmount, Long downAmount,
                                        Long finalAmount, LocalDate startDate, LocalDate endDate,
                                        WorkStyle workStyle, WorkForm workForm, String workLocation,
                                        int inspectionDays, int paymentDays, int confidentialYears,
                                        BigDecimal penaltyRate, String specialTerms, String contentJson,
                                        Long pdfFileId, String esignProvider, String esignDocId,
                                        ContractStatus status, LocalDateTime signedAt,
                                        LocalDateTime completedAt, LocalDateTime terminatedAt,
                                        PartyRole terminatedBy, LocalDate retentionUntil,
                                        LocalDateTime createdAt, List<ContractSignature> signatures) {
        return new Contract(id, contractNo, negotiationId, projectId, positionId, clientId, freelancerId,
                salaryAmount, totalAmount, downAmount, finalAmount, startDate, endDate,
                workStyle, workForm, workLocation, inspectionDays, paymentDays, confidentialYears,
                penaltyRate, specialTerms, contentJson, pdfFileId, esignProvider, esignDocId,
                status, signedAt, completedAt, terminatedAt, terminatedBy, retentionUntil, createdAt,
                new ArrayList<>(signatures));
    }

    /**
     * 계약 번호 채번. 번호에 id 가 들어가 INSERT 전에는 만들 수 없다.
     *
     * <p>정산번호와 같은 방식이다. 저장 직후 같은 트랜잭션에서 덮어쓴다.
     */
    public void assignContractNo(int year) {
        if (this.id == null || this.contractNo != null) {
            return;
        }
        this.contractNo = "CT-%d-%06d".formatted(year, this.id);
    }

    // ==========================================
    // 상태 전이
    // ==========================================

    /**
     * 서명. 양측이 다 서명하면 체결(SIGNED)된다.
     *
     * <p>체결 시점에 프리랜서 착수금 수수료가 발생한다(P27). 그 처리는 응용 계층이 한다.
     *
     * @return 이 서명으로 계약이 체결됐으면 true
     */
    public boolean sign(Long accountId, String verificationMethod, String ipAddress,
                        String userAgent, String timestampToken, Long signatureFileId) {
        requireStatus(ContractStatus.SIGN_PENDING);

        ContractSignature signature = findSignature(accountId);
        signature.sign(verificationMethod, ipAddress, userAgent, timestampToken, signatureFileId);

        if (!isFullySigned()) {
            return false;
        }
        this.status = ContractStatus.SIGNED;
        this.signedAt = LocalDateTime.now();
        return true;
    }

    /** 서명 거부. 한쪽이 거부하면 계약 전체가 끝난다. */
    public void reject(Long accountId, String reason) {
        requireStatus(ContractStatus.SIGN_PENDING);

        findSignature(accountId).reject(reason);
        this.status = ContractStatus.REJECTED;
    }

    /** 검수 완료. 성공보수 수수료 결제가 남아 있어 프로젝트는 아직 종료가 아니다. */
    public void complete() {
        requireStatus(ContractStatus.SIGNED);
        this.status = ContractStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 중도 파기. 파기 주체가 상대방 10% + 플랫폼 10% 를 부담한다(P32).
     *
     * <p>위약금 계산과 정산 생성은 정산 도메인이 한다. 여기서는 사실만 기록한다.
     */
    public void terminate(PartyRole terminatedBy, LocalDate retentionUntil) {
        if (this.status != ContractStatus.SIGNED && this.status != ContractStatus.COMPLETED) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_STATUS);
        }
        if (terminatedBy == null) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        this.status = ContractStatus.TERMINATED;
        this.terminatedAt = LocalDateTime.now();
        this.terminatedBy = terminatedBy;
        this.retentionUntil = retentionUntil;
    }

    /** 생성된 계약서 PDF 를 연결한다. 재생성하면 덮어쓴다. */
    public void attachPdf(Long pdfFileId) {
        this.pdfFileId = pdfFileId;
    }

    /**
     * 본문 자유 텍스트를 채우고 서명 대기로 넘긴다. 이 시점부터 서명할 수 있다.
     *
     * <p>{@code specialTerms} 는 <b>비어 있으면 덮지 않는다</b>. 협상에서 합의된 특약 원문이
     * 이미 들어 있는데, AI 가 실패했을 때 그것을 "없음"으로 지우면 합의 내용이 사라진다.
     *
     * @param contentJson  렌더링용 조항 스냅샷. 계약서를 다시 그릴 때 이 값만 있으면 된다
     * @param specialTerms 다듬어진 특약사항. null·공백이면 기존 원문을 유지한다
     * @return 이번 호출로 실제 넘어갔으면 true. 이미 넘어갔으면 false(비동기 재시도 방어)
     */
    public boolean completeDraft(String contentJson, String specialTerms) {
        if (this.status != ContractStatus.DRAFT) {
            return false;
        }
        this.contentJson = contentJson;

        if (specialTerms != null && !specialTerms.isBlank()) {
            this.specialTerms = specialTerms;
        }
        this.status = ContractStatus.SIGN_PENDING;
        return true;
    }

    // ==========================================
    // 조회
    // ==========================================

    public boolean isFullySigned() {
        return signatures.stream().filter(ContractSignature::isSigned).count() == PARTY_COUNT;
    }

    /** 열람 권한. 계약 당사자 두 명만 볼 수 있다. */
    public boolean isPartyOf(Long accountId) {
        return signatures.stream().anyMatch(s -> s.isOwnedBy(accountId));
    }

    public ContractSignature findSignature(Long accountId) {
        return signatures.stream()
                .filter(s -> s.isOwnedBy(accountId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ContractErrorCode.NOT_CONTRACT_PARTY));
    }

    /** 갑·을의 로그인 계정. 계약은 프로필 id 만 들고 있어 서명에서 꺼내 쓴다. */
    public Long accountIdOf(PartyRole partyRole) {
        return signatures.stream()
                .filter(s -> s.getPartyRole() == partyRole)
                .map(ContractSignature::getAccountId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ContractErrorCode.SIGNATURE_NOT_FOUND));
    }

    // ==========================================
    // 검증
    // ==========================================

    private void requireStatus(ContractStatus required) {
        if (this.status != required) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_STATUS);
        }
    }

    private static void validate(Long negotiationId, Long projectId, Long positionId,
                                 Long clientId, Long freelancerId, Long salaryAmount, int months,
                                 LocalDate startDate, LocalDate endDate,
                                 WorkStyle workStyle, WorkForm workForm, String specialTerms) {
        if (negotiationId == null || projectId == null || positionId == null
                || clientId == null || freelancerId == null) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        if (salaryAmount == null || salaryAmount <= 0 || months <= 0) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        if (workStyle == null || workForm == null) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        if (specialTerms != null && specialTerms.length() > MAX_SPECIAL_TERMS) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
    }
}
