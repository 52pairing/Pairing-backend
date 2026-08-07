package com.pairing.negotiation.presentation.api.response;

import com.pairing.negotiation.domain.model.NegotiationStatus;
import com.pairing.negotiation.domain.model.SenderType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [관리자] AI 협상 상세. (관리자 &gt; AI Agent 관리 &gt; 상세)
 *
 * <p>화면 탭이 "협상 로그"와 "원본 로그" 둘이다. 이 응답은 협상 로그 탭이고,
 * 원본 로그는 {@code GET /negotiations/admin/{id}/raw-logs} 로 따로 받는다.
 *
 * <p>당사자 화면과 달리 양쪽 에이전트의 제안·응답을 모두 보여준다.
 */
@Schema(description = "관리자 협상 상세 응답")
public record NegotiationAdminDetailResponse(

        @Schema(description = "협상 ID", example = "300") Long negotiationId,
        @Schema(description = "협상번호", example = "NEG-2026-001") String negotiationNo,
        @Schema(description = "프로젝트 ID", example = "1") Long projectId,
        @Schema(description = "프로젝트명", example = "쇼핑몰 관리자 페이지 리뉴얼") String projectTitle,
        @Schema(description = "클라이언트명", example = "삼성전자") String clientName,
        @Schema(description = "프리랜서명", example = "김프리") String freelancerName,
        @Schema(description = "상태") NegotiationStatus status,
        @Schema(description = "시작일") LocalDateTime startedAt,
        @Schema(description = "종료일. 진행 중이면 null") LocalDateTime endedAt,
        @Schema(description = "총 협상 횟수", example = "4") int totalRound,

        @Schema(description = "최종 협상 결과. 합의 전이면 null") FinalResult finalResult,
        @Schema(description = "라운드별 로그") List<RoundLog> roundLogs
) {

    @Schema(description = "최종 협상 결과")
    public record FinalResult(
            @Schema(description = "최종 금액 표기", example = "월 5,000,000원") String amountLabel,
            @Schema(description = "계약 기간 표기", example = "3개월") String periodLabel,
            @Schema(description = "근무 방식 표기", example = "혼합") String workStyleLabel,
            @Schema(description = "작업 범위", example = "프론트엔드 전체 리뉴얼") String workScope
    ) {
    }

    /** 화면에서 아코디언 한 칸. 접힌 상태는 헤더 4개만, 펼치면 나머지가 보인다. */
    @Schema(description = "라운드별 협상 로그")
    public record RoundLog(
            @Schema(description = "라운드 번호", example = "1") int roundNo,
            @Schema(description = "발신 주체") SenderType senderType,
            @Schema(description = "발신 주체 라벨", example = "클라이언트 Agent") String senderLabel,
            @Schema(description = "라운드 결과 라벨", example = "수정 제안") String resultLabel,
            @Schema(description = "발생 시각") LocalDateTime sentAt,

            @Schema(description = "제안 금액 표기", example = "월 4,500,000원") String proposedAmountLabel,
            @Schema(description = "계약 기간 표기", example = "3개월") String periodLabel,
            @Schema(description = "작업 범위", example = "프론트엔드 전체") String workScope,
            @Schema(description = "근무 조건", example = "주 2회 출근") String workCondition,
            @Schema(description = "제안 이유", example = "예산 범위 내에서 최저 제안으로 시작") String proposalReason,
            @Schema(description = "응답", example = "수정 제안") String response,
            @Schema(description = "응답 이유", example = "희망 단가보다 낮음, 출근 조건 조정 필요") String responseReason
    ) {
    }
}
