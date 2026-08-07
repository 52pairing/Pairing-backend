package com.pairing.matching.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 매칭 요청 거절. 거절한 프리랜서는 해당 프로젝트 재추천 대상에서 제외된다. */
@Schema(description = "매칭 요청 거절")
public record MatchingRejectRequest(

        @Schema(description = "거절 사유(선택)", example = "일정이 맞지 않습니다.")
        @Size(max = 255, message = "사유는 255자 이하여야 합니다.")
        String reason
) {
}
