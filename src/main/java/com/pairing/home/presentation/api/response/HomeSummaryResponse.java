package com.pairing.home.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 비로그인 메인 지표. (요구사항 R42)
 *
 * <p>화면에 숫자를 하드코딩하지 않도록 서버가 내려준다. 초기에는 고정값을 반환해도 된다.
 */
@Schema(description = "메인 지표 응답")
public record HomeSummaryResponse(

        @Schema(description = "프로젝트 완수율(%)", example = "99.0") Double completionRate,
        @Schema(description = "누적 프로젝트 등록 수", example = "5657") long projectCount,
        @Schema(description = "누적 협상 수", example = "6000") long negotiationCount,
        @Schema(description = "검증된 프리랜서 수", example = "5000") long freelancerCount,
        @Schema(description = "AI 매칭 고객만족도(5점 만점)", example = "4.8") Double satisfaction,
        @Schema(description = "누적 프로젝트 금액(원)", example = "300000000000") Long totalProjectAmount
) {
}
