package com.pairing.negotiation.application.result.admin;

import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.SenderType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 협상 상세(협상 로그 탭). 기본 정보 + 최종 결과 + 라운드별 로그.
 * 당사자 화면과 달리 양쪽 에이전트 제안·응답을 모두 보여준다(마스킹 없음).
 */
public record AdminDetail(
        Long negotiationId,
        Long projectId,
        String projectTitle,
        String clientName,
        String freelancerName,
        NegotiationStatus status,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        int totalRound,
        FinalResult finalResult,
        List<RoundLog> roundLogs
) {

    /** 최종 협상 결과(타결 시). 합의된 조건값에서 조립한다. */
    public record FinalResult(
            String amountLabel,
            String periodLabel,
            String workStyleLabel,
            String workScope
    ) {
    }

    /** 협상 로그 1건(제안 또는 응답). 조건 타입에 맞는 필드만 채운다. */
    public record RoundLog(
            int roundNo,
            SenderType senderType,
            String senderLabel,
            String resultLabel,
            LocalDateTime sentAt,
            String proposedAmountLabel,
            String periodLabel,
            String workScope,
            String workCondition,
            String proposalReason,
            String response,
            String responseReason
    ) {
    }
}
