package com.pairing.contract.infrastructure.mapper;

import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.model.ContractSignature;
import com.pairing.contract.infrastructure.persistence.ContractJpaEntity;
import com.pairing.contract.infrastructure.persistence.ContractSignatureJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 도메인 &lt;-&gt; 엔티티 변환.
 *
 * <p>MapStruct 를 쓰지 않고 직접 쓴다. 인자가 32개라 위치가 어긋나면 컴파일은 통과하고
 * 값만 뒤바뀐다. 한 줄에 하나씩 적어 눈으로 대조할 수 있게 했다.
 *
 * <p>서명은 부모의 {@code addSignature} 로 걸어야 FK 가 채워진다. 리스트에 넣기만 하면
 * {@code contract_id} 가 null 로 나간다.
 */
@Component
public class ContractMapper {

    public ContractJpaEntity toJpaEntity(Contract domain) {
        if (domain == null) {
            return null;
        }
        ContractJpaEntity entity = new ContractJpaEntity(
                domain.getId(),
                domain.getContractNo(),
                domain.getNegotiationId(),
                domain.getProjectId(),
                domain.getPositionId(),
                domain.getClientId(),
                domain.getFreelancerId(),
                domain.getSalaryAmount(),
                domain.getTotalAmount(),
                domain.getDownAmount(),
                domain.getFinalAmount(),
                domain.getStartDate(),
                domain.getEndDate(),
                domain.getWorkStyle(),
                domain.getWorkForm(),
                domain.getWorkLocation(),
                domain.getInspectionDays(),
                domain.getPaymentDays(),
                domain.getConfidentialYears(),
                domain.getPenaltyRate(),
                domain.getSpecialTerms(),
                domain.getContentJson(),
                domain.getPdfFileId(),
                domain.getEsignProvider(),
                domain.getEsignDocId(),
                domain.getStatus(),
                domain.getSignedAt(),
                domain.getCompletedAt(),
                domain.getTerminatedAt(),
                domain.getTerminatedBy(),
                domain.getRetentionUntil(),
                domain.getCreatedAt());

        domain.getSignatures().forEach(signature -> entity.addSignature(toSignatureEntity(signature)));
        return entity;
    }

    public Contract toDomain(ContractJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        List<ContractSignature> signatures = entity.getSignatures().stream()
                .map(this::toSignatureDomain)
                .toList();

        return Contract.reconstitute(
                entity.getId(),
                entity.getContractNo(),
                entity.getNegotiationId(),
                entity.getProjectId(),
                entity.getPositionId(),
                entity.getClientId(),
                entity.getFreelancerId(),
                entity.getSalaryAmount(),
                entity.getTotalAmount(),
                entity.getDownAmount(),
                entity.getFinalAmount(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getWorkStyle(),
                entity.getWorkForm(),
                entity.getWorkLocation(),
                entity.getInspectionDays(),
                entity.getPaymentDays(),
                entity.getConfidentialYears(),
                entity.getPenaltyRate(),
                entity.getSpecialTerms(),
                entity.getContentJson(),
                entity.getPdfFileId(),
                entity.getEsignProvider(),
                entity.getEsignDocId(),
                entity.getStatus(),
                entity.getSignedAt(),
                entity.getCompletedAt(),
                entity.getTerminatedAt(),
                entity.getTerminatedBy(),
                entity.getRetentionUntil(),
                entity.getCreatedAt(),
                signatures);
    }

    public ContractSignatureJpaEntity toSignatureEntity(ContractSignature domain) {
        return new ContractSignatureJpaEntity(
                domain.getId(),
                domain.getAccountId(),
                domain.getPartyRole(),
                domain.getStatus(),
                domain.getVerificationMethod(),
                domain.getSignedAt(),
                domain.getIpAddress(),
                domain.getUserAgent(),
                domain.getTimestampToken(),
                domain.getSignatureFileId(),
                domain.getProviderSignerId(),
                domain.getRejectReason());
    }

    private ContractSignature toSignatureDomain(ContractSignatureJpaEntity entity) {
        return ContractSignature.reconstitute(
                entity.getId(),
                entity.getAccountId(),
                entity.getPartyRole(),
                entity.getStatus(),
                entity.getVerificationMethod(),
                entity.getSignedAt(),
                entity.getIpAddress(),
                entity.getUserAgent(),
                entity.getTimestampToken(),
                entity.getSignatureFileId(),
                entity.getProviderSignerId(),
                entity.getRejectReason());
    }
}
