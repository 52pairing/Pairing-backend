package com.pairing.support.presentation.api.response;

import com.pairing.support.application.result.InquirySummaryResult;
import io.swagger.v3.oas.annotations.media.Schema;

/** [관리자] 1:1 문의 목록 상단 요약 카드. */
@Schema(description = "1:1 문의 요약")
public record InquirySummaryResponse(

        @Schema(description = "전체 문의", example = "4") long totalCount,
        @Schema(description = "답변 대기", example = "2") long pendingCount,
        @Schema(description = "답변 완료", example = "2") long answeredCount,
        @Schema(description = "오늘 접수", example = "1") long todayCount
) {

    public static InquirySummaryResponse from(InquirySummaryResult result) {
        return new InquirySummaryResponse(result.totalCount(), result.pendingCount(), result.answeredCount(),
                result.todayCount());
    }
}
