package com.pairing.admin.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/**
 * 관리자 대시보드. (관리자 &gt; 대시보드)
 *
 * <p>각 관리 화면의 요약 카드를 한 화면에 모은 것이다. 세부 목록은 도메인별 관리자 API 를 쓴다.
 */
@Schema(description = "관리자 대시보드 응답")
public record AdminDashboardResponse(

        @Schema(description = "기준일") LocalDate baseDate,
        @Schema(description = "회원 현황") Members members,
        @Schema(description = "프로젝트 현황") Projects projects,
        @Schema(description = "협상 현황") Negotiations negotiations,
        @Schema(description = "정산 현황") Settlements settlements,

        // 처리해야 할 일. 화면에서 클릭하면 해당 관리 화면으로 보낸다.
        @Schema(description = "처리 대기 항목") List<PendingItem> pendingItems
) {

    @Schema(description = "회원 현황")
    public record Members(
            @Schema(description = "전체", example = "6") long totalCount,
            @Schema(description = "이번 달 가입", example = "2") long newThisMonthCount,
            @Schema(description = "정지", example = "1") long suspendedCount
    ) {
    }

    @Schema(description = "프로젝트 현황")
    public record Projects(
            @Schema(description = "전체", example = "12") long totalCount,
            @Schema(description = "진행 중", example = "4") long inProgressCount,
            @Schema(description = "이번 달 등록", example = "3") long newThisMonthCount
    ) {
    }

    @Schema(description = "협상 현황")
    public record Negotiations(
            @Schema(description = "진행 중", example = "1") long inProgressCount,
            @Schema(description = "이번 달 성사", example = "2") long agreedThisMonthCount,
            @Schema(description = "이번 달 결렬", example = "1") long failedThisMonthCount
    ) {
    }

    @Schema(description = "정산 현황")
    public record Settlements(
            @Schema(description = "총 수익(원)", example = "125000000") long totalRevenue,
            @Schema(description = "이번 달 수익(원)", example = "8400000") long thisMonthRevenue,
            @Schema(description = "미납 금액(원)", example = "450000") long overdueAmount
    ) {
    }

    @Schema(description = "처리 대기 항목")
    public record PendingItem(
            @Schema(description = "구분", example = "INQUIRY") String type,
            @Schema(description = "표시 문구", example = "답변 대기 문의") String label,
            @Schema(description = "건수", example = "3") long count
    ) {
    }
}
