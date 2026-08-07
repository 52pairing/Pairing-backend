package com.pairing.global.common.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 상태 변경 이력 한 줄. 관리자 상세 화면의 "상태 이력" 표에 그대로 들어간다.
 *
 * <p>프로젝트·정산·계약이 같은 형태(일시·상태·처리자·비고)라 공통 응답으로 뒀다.
 */
@Schema(description = "상태 이력")
public record StatusHistoryResponse(

        @Schema(description = "변경 일시") LocalDateTime changedAt,
        @Schema(description = "상태 코드", example = "RECRUITING") String status,
        @Schema(description = "상태 라벨", example = "모집중") String statusLabel,
        @Schema(description = "처리자", example = "관리자 시스템") String actor,
        @Schema(description = "비고", example = "자동 전환") String note
) {
}
