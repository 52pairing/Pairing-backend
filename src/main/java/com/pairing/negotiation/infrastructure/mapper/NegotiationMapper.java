package com.pairing.negotiation.infrastructure.mapper;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.infrastructure.persistence.NegotiationConditionJpaEntity;
import com.pairing.negotiation.infrastructure.persistence.NegotiationJpaEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 협상 애그리거트 ↔ JPA 매핑. 도메인 생성자가 닫혀 있어 reconstitute 정적 팩토리로 복원한다.
 * 조건(자식)은 협상과 함께 매핑한다.
 */
@Component
public class NegotiationMapper {

    public NegotiationJpaEntity toJpaEntity(Negotiation d) {
        List<NegotiationConditionJpaEntity> conds = new ArrayList<>();
        for (NegotiationCondition c : d.getConditions()) {
            conds.add(toConditionJpa(c));
        }
        return new NegotiationJpaEntity(
                d.getId(), d.getRequestId(), d.getProjectId(), d.getPositionId(), d.getFreelancerId(),
                d.getStatus(), d.getTotalRound(), d.getAgreedAmount(), d.getBudgetCap(),
                d.getFreelancerMonthlyPay(), d.getFloorAmount(),
                d.getAiOutAt(), d.getStartedAt(), d.getEndedAt(), d.getEndReason(),
                d.getClientLastReadAt(), d.getFreelancerLastReadAt(), conds);
    }

    public Negotiation toDomain(NegotiationJpaEntity e) {
        if (e == null) {
            return null;
        }
        List<NegotiationCondition> conds = new ArrayList<>();
        for (NegotiationConditionJpaEntity c : e.getConditions()) {
            conds.add(toConditionDomain(c, e.getId()));
        }
        return reconstitute(e, conds);
    }

    /**
     * 목록(요약)용 매핑. 조건(자식)을 건드리지 않는다 — 목록 쿼리는 조건을 로드하지 않으므로
     * 여기서 getConditions() 를 접근하면 지연 로딩(N+1)이 발생한다.
     */
    public Negotiation toDomainSummary(NegotiationJpaEntity e) {
        if (e == null) {
            return null;
        }
        return reconstitute(e, List.of());
    }

    private Negotiation reconstitute(NegotiationJpaEntity e, List<NegotiationCondition> conds) {
        return Negotiation.reconstitute(
                e.getId(), e.getRequestId(), e.getProjectId(), e.getPositionId(), e.getFreelancerId(),
                e.getStatus(), e.getTotalRound(), e.getAgreedAmount(), e.getBudgetCap(),
                e.getFreelancerMonthlyPay(), e.getFloorAmount(),
                e.getAiOutAt(), e.getStartedAt(), e.getEndedAt(), e.getEndReason(),
                e.getClientLastReadAt(), e.getFreelancerLastReadAt(), conds);
    }

    private NegotiationConditionJpaEntity toConditionJpa(NegotiationCondition c) {
        return new NegotiationConditionJpaEntity(
                c.getId(), c.getConditionType(), c.getClientValue(), c.getFreelancerValue(),
                c.getClientFloor(), c.getFreelancerFloor(), c.getAgreedValue(), c.getStatus(),
                c.getRoundCount(), c.getSortOrder(), c.getAgreedAt());
    }

    private NegotiationCondition toConditionDomain(NegotiationConditionJpaEntity c, Long negotiationId) {
        return NegotiationCondition.reconstitute(
                c.getId(), negotiationId, c.getConditionType(), c.getClientValue(), c.getFreelancerValue(),
                c.getClientFloor(), c.getFreelancerFloor(), c.getAgreedValue(), c.getStatus(),
                c.getRoundCount(), c.getSortOrder(), c.getAgreedAt());
    }
}
