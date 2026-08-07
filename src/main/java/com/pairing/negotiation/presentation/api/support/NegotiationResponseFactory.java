package com.pairing.negotiation.presentation.api.support;

import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.presentation.api.response.NegotiationResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationSummaryResponse;

import java.util.List;

/**
 * 협상 조회 응답 조립. 뷰어 role 에 맞춰 마지노선을 본인 것(myFloor)만 노출하고,
 * 상대 마지노선은 절대 담지 않는다.
 *
 * <p>아직 값이 없는 필드는 이후 마일스톤에서 채운다.
 * <ul>
 *   <li>제안값/근거(proposedValue/reason), 마지막 제안(lastProposalBy/At), 응답 대기(waitingForMe) → M4 메시지</li>
 *   <li>표시용 협상번호(negotiationNo) → 스키마에 원천 없음, 포맷 확정 후</li>
 *   <li>채팅방(chatRoomId) → M4</li>
 * </ul>
 */
public final class NegotiationResponseFactory {

    private NegotiationResponseFactory() {
    }

    public static NegotiationResponse detail(NegotiationView view) {
        Negotiation n = view.negotiation();
        PartyRole role = view.viewerRole();

        List<NegotiationResponse.Condition> conditions = n.getConditions().stream()
                .map(c -> condition(c, role))
                .toList();

        return new NegotiationResponse(
                n.getId(),
                n.getProjectId(),
                view.projectTitle(),
                n.getPositionId(),
                counterpartName(view),
                n.getStatus(),
                n.getTotalRound(),
                Negotiation.MAX_ROUND,
                n.getAgreedAmount(),
                null,                       // TODO(M4): chatRoomId
                n.getAiOutAt(),
                false,                      // finalApprovalRequired: 15회 자동 결렬 채택으로 항상 false
                conditions
        );
    }

    public static NegotiationSummaryResponse summary(NegotiationView view) {
        Negotiation n = view.negotiation();
        return new NegotiationSummaryResponse(
                n.getId(),
                null,                       // TODO: negotiationNo(표시용, 원천 없음)
                n.getProjectId(),
                view.projectTitle(),
                counterpartName(view),
                view.clientName(),
                view.freelancerName(),
                n.getStatus(),
                n.getTotalRound(),
                false,                      // TODO(M4): waitingForMe
                null,                       // TODO(M4): lastProposalBy
                null,                       // TODO(M4): lastProposalAt
                n.getStartedAt(),
                n.getEndedAt()
        );
    }

    /** 뷰어 기준 상대 이름: 클라가 보면 프리 이름, 프리가 보면 클라 회사명. */
    private static String counterpartName(NegotiationView view) {
        return view.viewerRole() == PartyRole.CLIENT ? view.freelancerName() : view.clientName();
    }

    private static NegotiationResponse.Condition condition(NegotiationCondition c, PartyRole role) {
        return new NegotiationResponse.Condition(
                c.getId(),
                c.getConditionType(),
                c.getClientValue(),         // 희망값(공개)
                c.getFreelancerValue(),     // 희망값(공개)
                null,                       // TODO(M4): proposedValue
                null,                       // TODO(M4): reason
                c.getAgreedValue(),
                c.getStatus(),
                c.getRoundCount(),
                c.floorForViewer(role)      // 뷰어 본인 마지노선만
        );
    }
}
