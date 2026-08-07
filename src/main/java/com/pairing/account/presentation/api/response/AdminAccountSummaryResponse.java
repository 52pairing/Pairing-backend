package com.pairing.account.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관리자 회원 관리 상단 요약 카드. (요구사항 R37) */
@Schema(description = "관리자 회원 요약")
public record AdminAccountSummaryResponse(

        @Schema(description = "전체 회원 수", example = "6")
        long totalCount,

        @Schema(description = "정상 회원 수", example = "4")
        long activeCount,

        @Schema(description = "정지 회원 수", example = "1")
        long suspendedCount,

        @Schema(description = "탈퇴 회원 수", example = "1")
        long withdrawnCount,

        @Schema(description = "클라이언트 수", example = "3")
        long clientCount,

        @Schema(description = "프리랜서 수", example = "3")
        long freelancerCount
) {
}
