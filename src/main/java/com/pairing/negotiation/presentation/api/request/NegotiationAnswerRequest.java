package com.pairing.negotiation.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 조건별 응답. (요구사항 R08, R09)
 *
 * <p>AI 가 여러 조건을 한 번의 제안으로 묶어 보내므로, 응답도 조건 목록으로 한 번에 받는다.
 * NO 로 답할 때는 직접 입력값(proposedValue)이 필요하다.
 */
@Schema(description = "협상 조건 응답")
public record NegotiationAnswerRequest(

        @Schema(description = "응답할 라운드 번호. 이전 라운드에 대한 늦은 응답을 걸러낸다.", example = "3")
        @NotNull(message = "라운드 번호는 필수입니다.")
        Integer roundNo,

        @Schema(description = "조건별 응답 목록")
        @NotEmpty(message = "응답할 조건이 없습니다.")
        @Valid
        List<Answer> answers
) {

    @Schema(description = "조건 1건에 대한 응답")
    public record Answer(

            @Schema(description = "협상 조건 ID", example = "401")
            @NotNull(message = "조건 ID는 필수입니다.")
            Long conditionId,

            @Schema(description = "수락 여부. false 면 proposedValue 가 필요하다.", example = "false")
            @NotNull(message = "수락 여부는 필수입니다.")
            Boolean accepted,

            @Schema(description = "직접 입력값. 금액은 숫자 문자열, 그 외는 enum 값", example = "2200000")
            @Size(max = 255, message = "255자 이하여야 합니다.")
            String proposedValue
    ) {
    }
}
