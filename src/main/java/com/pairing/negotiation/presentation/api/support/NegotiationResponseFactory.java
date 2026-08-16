package com.pairing.negotiation.presentation.api.support;

import com.pairing.negotiation.application.result.NegotiationView;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.NegotiationCondition;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.PartyRole;
import com.pairing.negotiation.presentation.api.response.NegotiationMessageResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationSummaryResponse;

import java.util.List;

/**
 * 협상 조회 응답 조립. 뷰어 role 에 맞춰 마지노선을 본인 것(myFloor)만 노출하고,
 * 상대 마지노선은 절대 담지 않는다. 표시 파생 값(제안값·근거, 마지막 제안, 응답 대기, 채팅방)은
 * 서비스가 {@link NegotiationView} 에 채워 넘긴다.
 *
 * <p>표시용 협상번호(negotiationNo)는 스키마에 원천이 없어 협상 ID 로 파생한다(포맷 확정 후 교체).
 */
public final class NegotiationResponseFactory {

    private NegotiationResponseFactory() {
    }

    public static NegotiationResponse detail(NegotiationView view) {
        Negotiation n = view.negotiation();
        PartyRole role = view.viewerRole();

        List<NegotiationResponse.Condition> conditions = n.getConditions().stream()
                .map(c -> condition(c, role, view.conditionProposals().get(c.getId())))
                .toList();

        return new NegotiationResponse(
                n.getId(),
                n.getProjectId(),
                view.projectTitle(),
                n.getPositionId(),
                counterpartName(view),
                role,
                view.waitingForMe(),
                n.getAgentState(),
                n.getStatus(),
                n.getTotalRound(),
                Negotiation.MAX_ROUND,
                n.getAgreedAmount(),
                view.chatRoomId(),          // 타결 후 채팅 이동용(없으면 null)
                n.getAiOutAt(),
                n.getEndedAt(),
                // 종료 사유는 결렬일 때만 내보낸다. 타결에도 값이 들어가면 "왜 끝났는지"를 묻는
                // 화면이 성공 카드에까지 사유를 붙이게 된다 — 타결은 사유가 없는 게 맞다.
                n.getStatus() == NegotiationStatus.FAILED ? n.getEndReason() : null,
                false,                      // finalApprovalRequired: 15회 자동 결렬 채택으로 항상 false
                n.isFinalOffer(),
                n.hasAcceptedFinalOffer(role),
                n.hasAcceptedFinalOffer(role.opposite()),
                conditions
        );
    }

    public static NegotiationSummaryResponse summary(NegotiationView view) {
        Negotiation n = view.negotiation();
        return new NegotiationSummaryResponse(
                n.getId(),
                NegotiationAdminResponseFactory.negotiationNo(n.getId(), n.getStartedAt()),
                n.getProjectId(),
                view.projectTitle(),
                counterpartName(view),
                view.clientName(),
                view.freelancerName(),
                n.getStatus(),
                n.getTotalRound(),
                n.isFinalOffer(),
                view.waitingForMe(),
                n.getAgentState(),
                view.lastProposalBy(),
                view.lastProposalAt(),
                n.getStartedAt(),
                n.getEndedAt()
        );
    }

    /** 협상 로그 1건. conditionType 은 conditionId → 조건 타입 매핑에서 넘겨받는다(시스템 안내는 null). */
    public static NegotiationMessageResponse message(NegotiationMessage m, ConditionType conditionType) {
        return new NegotiationMessageResponse(
                m.getId(), m.getRoundNo(), m.getSenderType(), m.getMessageType(), conditionType,
                m.getContent(), m.getReason(), m.getProposedValue(), m.getResponse(), m.getCreatedAt());
    }

    /** 뷰어 기준 상대 이름: 클라가 보면 프리 이름, 프리가 보면 클라 회사명. */
    private static String counterpartName(NegotiationView view) {
        return view.viewerRole() == PartyRole.CLIENT ? view.freelancerName() : view.clientName();
    }

    private static NegotiationResponse.Condition condition(NegotiationCondition c, PartyRole role,
                                                           NegotiationView.ConditionProposal proposal) {
        return new NegotiationResponse.Condition(
                c.getId(),
                c.getConditionType(),
                c.getClientValue(),         // 희망값(공개)
                c.getFreelancerValue(),     // 희망값(공개)
                proposal != null ? proposal.proposedValue() : null,   // 현재 AI 제안값
                proposal != null ? proposal.reason() : null,          // 제안 근거
                c.getAgreedValue(),
                c.getStatus(),
                c.getRoundCount(),
                c.floorForViewer(role),     // 뷰어 본인 마지노선만
                // 화면이 마지노선 안내 문구를 맞게 쓰려면 비교 방식을 알아야 한다.
                // 프론트가 조건 타입으로 다시 판단하면 규칙이 두 곳에 생긴다.
                c.getConditionType().getFloorComparison(),
                // 방향(뷰어 기준). 화면은 이걸로 이상/이하를 정한다 — role 추론 금지(시작일은 프리도 상한).
                c.getConditionType().floorDirectionFor(role),
                c.getCompromiseValue()      // 최종 절충값(최종 절충 단계의 미합의 조건에만)
        );
    }
}
