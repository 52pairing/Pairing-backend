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
import java.util.UUID;

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

    /**
     * 저장 전 임시 계약번호. 최종 번호가 id 를 포함하는데 INSERT 전에는 id 가 없다.
     *
     * <p>{@code contract_no} 가 NOT NULL + UNIQUE 라 null 로는 저장 자체가 안 된다. 정산번호와
     * 같은 방식으로 겹치지 않는 임시값을 넣고 {@link #assignContractNo} 로 덮어쓴다.
     */
    private static final String TEMP_NO_PREFIX = "TMP-";

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

    /**
     * 체결 시점 정산 계좌. 계약서 제5조에 찍힌 <b>그 한 줄</b>을 암호문으로 굳혀 둔다.
     *
     * <p>이게 없으면 프리랜서가 마이페이지에서 계좌를 바꿨을 때 <b>이미 체결된 계약서의 표시까지
     * 따라 바뀐다.</b> 계약은 5년 보관 대상이라 그때 그 문서가 그대로 남아야 한다.
     *
     * <p>체결 전에는 비어 있고, 그때는 현재 계좌를 그대로 보여준다. 서명 전이라 문제되지 않는다.
     * 이 필드가 생기기 전에 체결된 계약도 비어 있어 예전처럼 동작한다.
     */
    private byte[] settlementAccountEnc;

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
                     byte[] settlementAccountEnc,
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

        return new Contract(null, TEMP_NO_PREFIX + UUID.randomUUID(),
                negotiationId, projectId, positionId, clientId, freelancerId,
                salaryAmount, salaryAmount * months, NO_SPLIT, NO_SPLIT,
                startDate, endDate, workStyle, workForm,
                workStyle == WorkStyle.ONSITE ? workLocation : null,
                DEFAULT_INSPECTION_DAYS, DEFAULT_PAYMENT_DAYS, DEFAULT_CONFIDENTIAL_YEARS,
                DEFAULT_PENALTY_RATE, specialTerms, null, null, null, null, null,
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
                                        Long pdfFileId, byte[] settlementAccountEnc,
                                        String esignProvider, String esignDocId,
                                        ContractStatus status, LocalDateTime signedAt,
                                        LocalDateTime completedAt, LocalDateTime terminatedAt,
                                        PartyRole terminatedBy, LocalDate retentionUntil,
                                        LocalDateTime createdAt, List<ContractSignature> signatures) {
        return new Contract(id, contractNo, negotiationId, projectId, positionId, clientId, freelancerId,
                salaryAmount, totalAmount, downAmount, finalAmount, startDate, endDate,
                workStyle, workForm, workLocation, inspectionDays, paymentDays, confidentialYears,
                penaltyRate, specialTerms, contentJson, pdfFileId, settlementAccountEnc,
                esignProvider, esignDocId,
                status, signedAt, completedAt, terminatedAt, terminatedBy, retentionUntil, createdAt,
                new ArrayList<>(signatures));
    }

    /**
     * 계약 번호 채번. 번호에 id 가 들어가 INSERT 전에는 만들 수 없다.
     *
     * <p>정산번호와 같은 방식이다. 저장 직후 같은 트랜잭션에서 덮어쓴다.
     */
    public void assignContractNo(int year) {
        if (this.id == null) {
            return;
        }
        // 이미 확정된 번호는 건드리지 않는다. 임시번호일 때만 덮어쓴다.
        if (this.contractNo != null && !this.contractNo.startsWith(TEMP_NO_PREFIX)) {
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

    /**
     * 업무 시작. 전원 계약 + 전원 착수금 결제로 프로젝트가 진행중이 되면 따라 넘어간다(P47).
     *
     * <p>파기·거부된 계약은 건드리지 않는다. 프로젝트 이벤트는 그 프로젝트의 계약 전부에 오는데,
     * 중도 파기된 사람까지 진행중으로 되돌리면 안 된다.
     *
     * @return 이번 호출로 실제 바뀌었으면 true
     */
    public boolean startProgress() {
        if (this.status != ContractStatus.SIGNED) {
            return false;
        }
        this.status = ContractStatus.IN_PROGRESS;
        return true;
    }

    /**
     * 클라이언트가 프로젝트를 완료 처리했다. 성공보수 수수료 결제가 남아 정산 대기다(P32).
     *
     * <p>착수금 미납으로 아직 SIGNED 에 머문 계약도 함께 넘긴다. 프로젝트가 완료 처리됐다는 것은
     * 일이 끝났다는 뜻이고, 미납은 정산이 따로 쫓는다.
     */
    public boolean requestCompletion() {
        if (this.status != ContractStatus.SIGNED && this.status != ContractStatus.IN_PROGRESS) {
            return false;
        }
        this.status = ContractStatus.COMPLETION_PENDING;
        return true;
    }

    /** 성공보수 결제까지 끝나 계약이 종료됐다. 리뷰는 이 시점부터 열린다(P51). */
    public boolean complete() {
        if (this.status != ContractStatus.COMPLETION_PENDING) {
            return false;
        }
        this.status = ContractStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 중도 파기. 파기 주체가 상대방 10% + 플랫폼 10% 를 부담한다(P32).
     *
     * <p>위약금 계산과 정산 생성은 정산 도메인이 한다. 여기서는 사실만 기록한다.
     */
    public void terminate(PartyRole terminatedBy, LocalDate retentionUntil) {
        // 체결된 계약만 파기할 수 있다. 진행중·정산 대기도 체결 이후라 대상이다.
        if (!this.status.isConcluded()) {
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
     * 체결 시점 정산 계좌를 굳힌다. 체결 처리에서 한 번만 부른다.
     *
     * <p><b>이미 굳혀둔 값은 덮어쓰지 않는다.</b> 체결 이후에 바뀐 계좌가 계약서에 들어가면
     * 동결하는 의미가 없다. 체결이 재시도돼도 처음 값이 남는다.
     *
     * <p>계좌가 없는 프리랜서도 있다. 그때는 비워 두고 계약서에 "-" 로 나간다.
     * 계좌 때문에 체결이 막히면 안 된다.
     */
    public void freezeSettlementAccount(byte[] snapshot) {
        if (this.settlementAccountEnc == null) {
            this.settlementAccountEnc = snapshot;
        }
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

    /**
     * 그 당사자가 서명을 마쳤는가.
     *
     * <p>목록 카드가 "클라이언트 서명 ○ / 프리랜서 서명 ✓" 를 그리는 데 쓴다. 상세와 달리 목록은
     * 서명 전체를 내려주지 않으므로 역할별로 물어본다.
     */
    public boolean isSignedBy(PartyRole partyRole) {
        return signatures.stream()
                .anyMatch(s -> s.getPartyRole() == partyRole && s.isSigned());
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
