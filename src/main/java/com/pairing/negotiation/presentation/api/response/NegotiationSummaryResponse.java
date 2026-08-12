package com.pairing.negotiation.presentation.api.response;

import com.pairing.negotiation.domain.model.NegotiationAgentState;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.SenderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 협상 목록 항목. */
@Schema(description = "협상 목록 항목")
public record NegotiationSummaryResponse(

        @Schema(description = "협상 ID", example = "300") Long negotiationId,
        @Schema(description = "화면 표시용 협상번호", example = "NEG-2026-001") String negotiationNo,
        @Schema(description = "프로젝트 ID", example = "1") Long projectId,
        @Schema(description = "프로젝트명", example = "페어링 웹 리뉴얼") String projectTitle,
        @Schema(description = "상대 이름. 내 목록에서 쓴다", example = "홍길동") String counterpartName,
        @Schema(description = "클라이언트명. 관리자 목록에서 쓴다", example = "삼성전자") String clientName,
        @Schema(description = "프리랜서명. 관리자 목록에서 쓴다", example = "김프리") String freelancerName,
        @Schema(description = "상태") NegotiationStatus status,
        @Schema(description = "진행 라운드 수", example = "3") int totalRound,
        @Schema(description = "내 응답 대기 여부", example = "true") boolean waitingForMe,
        @Schema(description = "대리인(AI) 실행 상태. RUNNING 이면 카드에 진행 표시를 띄운다. "
                + "자세한 판정은 상세 응답의 같은 필드 설명 참고", example = "IDLE")
        NegotiationAgentState agentState,
        @Schema(description = "마지막 제안 주체. 협상 탭 카드 '마지막 제안: OO' 표시") SenderType lastProposalBy,
        @Schema(description = "마지막 제안 시각. '30분 전' 계산용") LocalDateTime lastProposalAt,
        @Schema(description = "시작 시각") LocalDateTime startedAt,
        @Schema(description = "종료 시각") LocalDateTime endedAt
) {
}
