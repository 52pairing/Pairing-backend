package com.pairing.freelancer.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 매칭 설정 현재 값. */
@Schema(description = "매칭 설정")
public record MatchingSettingsResponse(

        @Schema(description = "AI 매칭 활용 동의", example = "true") boolean aiMatchingAgreed,
        @Schema(description = "매칭 일시 중지", example = "false") boolean matchingPaused,
        @Schema(description = "추천 대상에 포함되는지. 이력서 미완성이면 false", example = "true") boolean matchable,
        @Schema(description = "추천 대상에서 빠진 사유. matchable 이 true 면 null",
                example = "이력서를 완성해야 추천 대상에 포함됩니다.") String unmatchableReason
) {
}
