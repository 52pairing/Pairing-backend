package com.pairing.contract.domain.model;

import com.pairing.global.exception.BusinessException;
import com.pairing.contract.exception.ContractErrorCode;
import com.pairing.meta.domain.model.PartyRole;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 당사자 1인의 서명. 계약 애그리거트 안에서만 만들어진다.
 *
 * <p>계약 1건에 갑(CLIENT)·을(FREELANCER) 두 건이 계약 생성과 동시에 PENDING 으로 만들어진다.
 * 나중에 추가되지 않는다.
 *
 * <p>서명 시각·IP·User-Agent 를 남기는 이유는 분쟁 시 증거력 때문이다. 값이 없으면
 * "누가 언제 어디서 서명했는지" 를 다투게 된다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractSignature {

    private static final int MAX_REJECT_REASON = 255;

    private Long id;
    private Long accountId;
    private PartyRole partyRole;
    private SignatureStatus status;

    private String verificationMethod;
    private LocalDateTime signedAt;
    private String ipAddress;
    private String userAgent;
    private String timestampToken;
    private Long signatureFileId;
    private String providerSignerId;
    private String rejectReason;

    private ContractSignature(Long id, Long accountId, PartyRole partyRole, SignatureStatus status,
                              String verificationMethod, LocalDateTime signedAt, String ipAddress,
                              String userAgent, String timestampToken, Long signatureFileId,
                              String providerSignerId, String rejectReason) {
        this.id = id;
        this.accountId = accountId;
        this.partyRole = partyRole;
        this.status = status;
        this.verificationMethod = verificationMethod;
        this.signedAt = signedAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.timestampToken = timestampToken;
        this.signatureFileId = signatureFileId;
        this.providerSignerId = providerSignerId;
        this.rejectReason = rejectReason;
    }

    /** 서명 대기 상태로 시작한다. 계약 생성 시 갑·을 두 건을 함께 만든다. */
    static ContractSignature create(Long accountId, PartyRole partyRole) {
        if (accountId == null || partyRole == null) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        return new ContractSignature(null, accountId, partyRole, SignatureStatus.PENDING,
                null, null, null, null, null, null, null, null);
    }

    public static ContractSignature reconstitute(Long id, Long accountId, PartyRole partyRole,
                                                 SignatureStatus status, String verificationMethod,
                                                 LocalDateTime signedAt, String ipAddress, String userAgent,
                                                 String timestampToken, Long signatureFileId,
                                                 String providerSignerId, String rejectReason) {
        return new ContractSignature(id, accountId, partyRole, status, verificationMethod, signedAt,
                ipAddress, userAgent, timestampToken, signatureFileId, providerSignerId, rejectReason);
    }

    /**
     * 서명. 증거 정보를 함께 남긴다.
     *
     * <p>거부한 뒤 다시 서명할 수는 없다. 거부는 계약을 끝내는 행위라 되돌리려면 새 계약을 만든다.
     */
    void sign(String verificationMethod, String ipAddress, String userAgent,
              String timestampToken, Long signatureFileId) {
        if (this.status != SignatureStatus.PENDING) {
            throw new BusinessException(ContractErrorCode.ALREADY_SIGNED);
        }
        this.status = SignatureStatus.SIGNED;
        this.signedAt = LocalDateTime.now();
        this.verificationMethod = verificationMethod;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.timestampToken = timestampToken;
        this.signatureFileId = signatureFileId;
    }

    /** 서명 거부. 사유는 상대에게 그대로 보여준다. */
    void reject(String reason) {
        if (this.status != SignatureStatus.PENDING) {
            throw new BusinessException(ContractErrorCode.ALREADY_SIGNED);
        }
        if (reason != null && reason.length() > MAX_REJECT_REASON) {
            throw new BusinessException(ContractErrorCode.INVALID_CONTRACT_FIELD);
        }
        this.status = SignatureStatus.REJECTED;
        this.rejectReason = reason;
    }

    public boolean isSigned() {
        return this.status == SignatureStatus.SIGNED;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.accountId.equals(accountId);
    }
}
