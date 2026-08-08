package com.pairing.negotiation.application.result;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.PartyRole;

/**
 * 뷰어 관점이 결정된 협상 조회 결과. 애그리거트 + 이 요청자의 role + 표시용 값을 함께 담아,
 * 프레젠테이션이 role 에 맞춰(myFloor·상대 이름 등) 응답을 조립할 수 있게 한다.
 *
 * <p>{@code clientName}(회사명)/{@code freelancerName}(프리 이름)은 양측 표시용으로 채워지고,
 * "상대 이름(counterpartName)"은 프레젠테이션이 role 로 골라 쓴다.
 *
 * <p>{@code chatRoomId} 는 타결 후 "채팅으로 이어가기" 이동용. 상세 조회에서만 채우고(목록은 null),
 * 채팅방이 아직 없으면 null 이다.
 */
public record NegotiationView(
        Negotiation negotiation,
        PartyRole viewerRole,
        String projectTitle,
        String clientName,
        String freelancerName,
        Long chatRoomId
) {
}
