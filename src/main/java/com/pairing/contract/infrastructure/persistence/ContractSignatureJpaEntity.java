package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.SignatureStatus;
import com.pairing.meta.domain.model.PartyRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * contract_signature 테이블 매핑.
 *
 * <p>created_at / updated_at 은 DB 기본값과 트리거가 채운다.
 */
@Entity
@Table(name = "contract_signature")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContractSignatureJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private ContractJpaEntity contract;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_role", nullable = false, length = 20)
    private PartyRole partyRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SignatureStatus status;

    @Column(name = "verification_method", length = 30)
    private String verificationMethod;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "timestamp_token", columnDefinition = "TEXT")
    private String timestampToken;

    @Column(name = "signature_file_id")
    private Long signatureFileId;

    @Column(name = "provider_signer_id", length = 255)
    private String providerSignerId;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    public ContractSignatureJpaEntity(Long id, Long accountId, PartyRole partyRole, SignatureStatus status,
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

    /** 양방향 연관은 부모가 걸어준다. 한쪽만 세팅되면 FK 가 null 로 나간다. */
    void assignContract(ContractJpaEntity contract) {
        this.contract = contract;
    }

    /** 서명 상태 갱신. 영속 엔티티에 스칼라만 반영한다. */
    void applyState(ContractSignatureJpaEntity source) {
        this.status = source.status;
        this.verificationMethod = source.verificationMethod;
        this.signedAt = source.signedAt;
        this.ipAddress = source.ipAddress;
        this.userAgent = source.userAgent;
        this.timestampToken = source.timestampToken;
        this.signatureFileId = source.signatureFileId;
        this.providerSignerId = source.providerSignerId;
        this.rejectReason = source.rejectReason;
    }
}
