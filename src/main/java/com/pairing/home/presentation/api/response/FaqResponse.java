package com.pairing.home.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 자주 찾는 질문. 메인 하단에 노출된다. */
@Schema(description = "FAQ 응답")
public record FaqResponse(

        @Schema(description = "질문", example = "페어링의 매칭 프로세스가 궁금합니다") String question,
        @Schema(description = "답변", example = "프로젝트 등록 → 프리랜서 매칭 → 협상 → 계약 성사 순으로 진행됩니다.") String answer,
        @Schema(description = "노출 순서", example = "1") int sortOrder
) {
}
