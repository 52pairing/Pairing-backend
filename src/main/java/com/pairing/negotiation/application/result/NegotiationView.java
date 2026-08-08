package com.pairing.negotiation.application.result;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.domain.model.SenderType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 뷰어 관점이 결정된 협상 조회 결과. 애그리거트 + 이 요청자의 role + 표시용 값을 함께 담아,
 * 프레젠테이션이 role 에 맞춰(myFloor·상대 이름 등) 응답을 조립할 수 있게 한다.
 *
 * <p>{@code clientName}(회사명)/{@code freelancerName}(프리 이름)은 양측 표시용으로 채워지고,
 * "상대 이름(counterpartName)"은 프레젠테이션이 role 로 골라 쓴다.
 *
 * <p>표시 전용 파생 값은 조회 종류에 따라 한쪽만 채운다({@link #forDetail}/{@link #forSummary}).
 * <ul>
 *   <li>상세(getDetail): {@code chatRoomId}, {@code conditionProposals}(조건별 현재 AI 제안값·근거)</li>
 *   <li>목록(findMine): {@code waitingForMe}, {@code lastProposalBy}, {@code lastProposalAt}</li>
 * </ul>
 */
public record NegotiationView(
        Negotiation negotiation,
        PartyRole viewerRole,
        String projectTitle,
        String clientName,
        String freelancerName,
        Long chatRoomId,
        boolean waitingForMe,
        SenderType lastProposalBy,
        LocalDateTime lastProposalAt,
        Map<Long, ConditionProposal> conditionProposals
) {

    /** 조건 카드에 인라인으로 보여줄 현재 AI 제안. */
    public record ConditionProposal(String proposedValue, String reason) {
    }

    /** 상세용: chatRoomId + 조건별 제안. 목록 전용 값은 기본값. */
    public static NegotiationView forDetail(Negotiation negotiation, PartyRole role, String title,
                                            String clientName, String freelancerName, Long chatRoomId,
                                            Map<Long, ConditionProposal> conditionProposals) {
        return new NegotiationView(negotiation, role, title, clientName, freelancerName, chatRoomId,
                false, null, null, conditionProposals == null ? Map.of() : conditionProposals);
    }

    /** 목록용: waitingForMe + 마지막 제안. 상세 전용 값은 기본값. */
    public static NegotiationView forSummary(Negotiation negotiation, PartyRole role, String title,
                                             String clientName, String freelancerName, boolean waitingForMe,
                                             SenderType lastProposalBy, LocalDateTime lastProposalAt) {
        return new NegotiationView(negotiation, role, title, clientName, freelancerName, null,
                waitingForMe, lastProposalBy, lastProposalAt, Map.of());
    }
}
