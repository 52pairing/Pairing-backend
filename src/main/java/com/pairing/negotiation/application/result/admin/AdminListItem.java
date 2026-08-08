package com.pairing.negotiation.application.result.admin;

import com.pairing.negotiation.domain.model.NegotiationStatus;

import java.time.LocalDateTime;

/**
 * 관리자 협상 목록 한 줄. 프로젝트명·클라이언트·프리랜서로 검색하고 상태로 필터한다.
 * (당사자 화면의 waitingForMe·lastProposal 은 관리자 목록에선 쓰지 않는다.)
 */
public record AdminListItem(
        Long negotiationId,
        Long projectId,
        String projectTitle,
        String clientName,
        String freelancerName,
        NegotiationStatus status,
        int totalRound,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
