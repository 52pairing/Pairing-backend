package com.pairing.grade.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 내 등급 현황. 마이페이지 "등급 및 혜택" 상단에 쓴다. */
@Schema(description = "내 등급")
public record MyGradeResponse(

        @Schema(description = "현재 등급 코드", example = "SILVER") String grade,
        @Schema(description = "현재 등급 이름", example = "실버") String label,
        @Schema(description = "완료 프로젝트 건수", example = "4") int completedProjectCount,
        @Schema(description = "평균 별점", example = "4.2") Double ratingAverage,
        @Schema(description = "다음 등급 코드. 최고 등급이면 null", example = "GOLD") String nextGrade,
        @Schema(description = "다음 등급까지 남은 조건. 최고 등급이면 null",
                example = "완료 건수 6건이 더 필요합니다.") String nextGradeGuide,
        @Schema(description = "등급 재산정 기준일 안내", example = "매월 1일 자동 산정") String checkedGuide
) {
}
