package com.pairing.negotiation.presentation.api.support;

import com.pairing.negotiation.application.result.admin.AdminDetail;
import com.pairing.negotiation.application.result.admin.AdminListItem;
import com.pairing.negotiation.application.result.admin.AdminRawLog;
import com.pairing.negotiation.application.result.admin.AdminSummary;
import com.pairing.negotiation.presentation.api.response.AgentRawLogResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationAdminDetailResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationAdminSummaryResponse;
import com.pairing.negotiation.presentation.api.response.NegotiationSummaryResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 관리자 협상 조회 결과(Admin*) → 응답 DTO 변환. */
public final class NegotiationAdminResponseFactory {

    private NegotiationAdminResponseFactory() {
    }

    /**
     * 화면 표시용 협상번호({@code NEG-2026-005}).
     *
     * <p>스키마에 번호 원천이 없어 <b>시작 연도 + 협상 ID</b> 로 파생한다. 그래서 그 해의 순번이
     * 아니라 전체 일련번호이며, 연도가 바뀌어도 001 부터 다시 시작하지 않는다. 관리자 화면의
     * 식별 표기일 뿐 업무 규칙에 쓰이지 않아 이 정도로 둔다. 진짜 연도별 순번이 필요해지면
     * 컬럼을 추가해야 한다(동시 생성 시 순번 충돌 처리도 함께).
     */
    public static String negotiationNo(Long negotiationId, LocalDateTime startedAt) {
        if (negotiationId == null) {
            return null;
        }
        int year = startedAt != null ? startedAt.getYear() : LocalDate.now().getYear();
        return String.format("NEG-%d-%03d", year, negotiationId);
    }

    public static NegotiationAdminSummaryResponse summary(AdminSummary s) {
        return new NegotiationAdminSummaryResponse(
                s.total(), s.inProgress(), s.agreed(), s.failed(), s.averageRound(), s.averageDurationDays());
    }

    /** 관리자 목록 항목. 당사자 전용 필드(counterpartName·waitingForMe·lastProposal)는 비운다. */
    public static NegotiationSummaryResponse listItem(AdminListItem item) {
        return new NegotiationSummaryResponse(
                item.negotiationId(),
                negotiationNo(item.negotiationId(), item.startedAt()),
                item.projectId(),
                item.projectTitle(),
                null,                       // counterpartName: 관리자 목록에선 미사용
                item.clientName(),
                item.freelancerName(),
                item.status(),
                item.totalRound(),
                false,                      // waitingForMe: 관리자 목록엔 해당 없음
                null,                       // lastProposalBy
                null,                       // lastProposalAt
                item.startedAt(),
                item.endedAt());
    }

    public static NegotiationAdminDetailResponse detail(AdminDetail d) {
        NegotiationAdminDetailResponse.FinalResult finalResult = d.finalResult() == null ? null
                : new NegotiationAdminDetailResponse.FinalResult(
                        d.finalResult().amountLabel(), d.finalResult().periodLabel(),
                        d.finalResult().workStyleLabel(), d.finalResult().workScope());

        var roundLogs = d.roundLogs().stream().map(r ->
                new NegotiationAdminDetailResponse.RoundLog(
                        r.roundNo(), r.senderType(), r.senderLabel(), r.resultLabel(), r.sentAt(),
                        r.proposedAmountLabel(), r.periodLabel(), r.workScope(), r.workCondition(),
                        r.proposalReason(), r.response(), r.responseReason())).toList();

        return new NegotiationAdminDetailResponse(
                d.negotiationId(), negotiationNo(d.negotiationId(), d.startedAt()), d.projectId(), d.projectTitle(),
                d.clientName(), d.freelancerName(), d.status(), d.startedAt(), d.endedAt(), d.totalRound(),
                finalResult, roundLogs);
    }

    public static AgentRawLogResponse rawLog(AdminRawLog log) {
        return new AgentRawLogResponse(
                log.logId(),
                null,                       // roundNo: ai_agent_log 에 라운드 컬럼 없음
                log.agentType(),
                log.model(),
                log.requestPayload(),
                log.responsePayload(),
                log.inputTokens(),
                log.outputTokens(),
                log.durationMs(),
                log.errorMessage(),
                log.createdAt());
    }
}
