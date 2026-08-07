package com.pairing.matching.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 매칭 요청 발송. (요구사항 R04)
 *
 * <p>포지션 모집 인원을 초과해 선택할 수 없다. 요청은 3일 뒤 자동 만료된다.
 */
@Schema(description = "매칭 요청 발송")
public record MatchingRequestCreateRequest(

        @Schema(description = "포지션 ID", example = "10")
        @NotNull(message = "포지션은 필수입니다.")
        Long positionId,

        @Schema(description = "요청할 후보의 candidateId 목록", example = "[100, 101]")
        @NotEmpty(message = "후보를 1명 이상 선택해야 합니다.")
        List<Long> candidateIds
) {
}
