package com.pairing.project.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * AI 사전 검수 요청. (요구사항 R30 / 프로젝트 등록 5단계)
 *
 * <p>등록 전이라 projectId 가 없으므로 입력 중인 포지션 조건만 보낸다.
 * 후보 수는 포지션 조건으로만 정해지므로 기본 정보·상세 내용은 보내지 않는다.
 */
@Schema(description = "AI 사전 검수 요청")
public record ProjectPreReviewRequest(

        @Schema(description = "확인할 포지션 조건 목록")
        @NotEmpty(message = "포지션 조건은 최소 1건입니다.")
        @Valid
        List<PositionRequest> positions
) {
}
