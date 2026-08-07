package com.pairing.negotiation.application.result;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.PartyRole;

/**
 * 뷰어 관점이 결정된 협상 조회 결과. 애그리거트 + 이 요청자의 role + 표시용 projectTitle 을 함께 담아,
 * 프레젠테이션이 role 에 맞춰(myFloor 등) 응답을 조립할 수 있게 한다.
 */
public record NegotiationView(
        Negotiation negotiation,
        PartyRole viewerRole,
        String projectTitle
) {
}
