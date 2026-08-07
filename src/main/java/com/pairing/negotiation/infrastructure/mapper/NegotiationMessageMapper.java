package com.pairing.negotiation.infrastructure.mapper;

import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.infrastructure.persistence.NegotiationMessageJpaEntity;
import org.springframework.stereotype.Component;

/** 협상 메시지 도메인 ↔ JPA 매핑. */
@Component
public class NegotiationMessageMapper {

    public NegotiationMessageJpaEntity toJpaEntity(NegotiationMessage m) {
        return new NegotiationMessageJpaEntity(m.getId(), m.getNegotiationId(), m.getConditionId(),
                m.getRoundNo(), m.getSenderType(), m.getMessageType(), m.getContent(), m.getReason(),
                m.getProposedValue(), m.getResponse(), m.getActingAccountId(), m.getPrevHash(),
                m.getContentHash(), m.getCreatedAt());
    }

    public NegotiationMessage toDomain(NegotiationMessageJpaEntity e) {
        if (e == null) {
            return null;
        }
        return NegotiationMessage.reconstitute(e.getId(), e.getNegotiationId(), e.getConditionId(),
                e.getRoundNo(), e.getSenderType(), e.getMessageType(), e.getContent(), e.getReason(),
                e.getProposedValue(), e.getResponse(), e.getActingAccountId(), e.getPrevHash(),
                e.getContentHash(), e.getCreatedAt());
    }
}
