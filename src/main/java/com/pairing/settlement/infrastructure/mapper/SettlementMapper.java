package com.pairing.settlement.infrastructure.mapper;

import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.infrastructure.persistence.SettlementJpaEntity;
import org.springframework.stereotype.Component;

/**
 * 도메인 <-> 엔티티 변환.
 *
 * <p>MapStruct 를 쓰지 않고 직접 쓴다. 필드가 평평하긴 하지만 인자가 19개라
 * 위치가 어긋나면 컴파일은 통과하고 값만 뒤바뀐다. 한 줄에 하나씩 적어 눈으로 대조할 수 있게 했다.
 */
@Component
public class SettlementMapper {

    public SettlementJpaEntity toJpaEntity(Settlement domain) {
        if (domain == null) {
            return null;
        }
        return new SettlementJpaEntity(
                domain.getId(),
                domain.getSettlementNo(),
                domain.getProjectId(),
                domain.getContractId(),
                domain.getPayerAccountId(),
                domain.getPayerRole(),
                domain.getPhase(),
                domain.getBaseAmount(),
                domain.getFeeRate(),
                domain.getGradeDiscount(),
                domain.getFeeAmount(),
                domain.getStatus(),
                domain.getPaymentMethodId(),
                domain.getApprovalNo(),
                domain.getFailReason(),
                domain.getOverdueReason(),
                domain.getDueDate(),
                domain.getPaidAt(),
                domain.getCreatedAt());
    }

    public Settlement toDomain(SettlementJpaEntity entity) {
        if (entity == null) {
            return null;
        }
        return Settlement.reconstitute(
                entity.getId(),
                entity.getSettlementNo(),
                entity.getProjectId(),
                entity.getContractId(),
                entity.getPayerAccountId(),
                entity.getPayerRole(),
                entity.getPhase(),
                entity.getBaseAmount(),
                entity.getFeeRate(),
                entity.getGradeDiscount(),
                entity.getFeeAmount(),
                entity.getStatus(),
                entity.getPaymentMethodId(),
                entity.getApprovalNo(),
                entity.getFailReason(),
                entity.getOverdueReason(),
                entity.getDueDate(),
                entity.getPaidAt(),
                entity.getCreatedAt());
    }
}
