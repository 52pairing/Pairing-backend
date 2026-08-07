package com.pairing.negotiation.presentation.api.request;

import com.pairing.negotiation.domain.model.ConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 협상 시작 시 설정하는 최소 조건(마지노선). (AI 협상 화면 1단계)
 *
 * <p>상대 AI 가 보낸 초기 제안을 보고 "여기까지는 받아들일 수 있다"는 선을 정한다.
 * 이 값은 상대에게 그대로 노출되지 않고, 내 에이전트가 협상 하한으로만 쓴다.
 *
 * <p>쟁점별로 값의 형태가 달라서 문자열로 받는다. 서버가 {@code conditionType} 에 맞춰 해석한다.
 * PAY 는 만원 단위 숫자, PERIOD 는 개월 수, WORK_STYLE 은 허용 가능한 근무형태를 쉼표로 이은 값이다.
 */
@Schema(description = "협상 시작 요청")
public record NegotiationStartRequest(

        @Schema(description = "쟁점별 마지노선")
        @NotEmpty(message = "최소 조건은 1건 이상입니다.")
        @Valid
        List<MinimumCondition> conditions
) {

    @Schema(description = "쟁점별 마지노선")
    public record MinimumCondition(

            @Schema(description = "쟁점 종류", example = "PAY")
            @NotNull(message = "쟁점 종류는 필수입니다.")
            ConditionType conditionType,

            @Schema(description = "마지노선 값", example = "350")
            @NotBlank(message = "마지노선 값은 필수입니다.")
            String value
    ) {
    }
}
