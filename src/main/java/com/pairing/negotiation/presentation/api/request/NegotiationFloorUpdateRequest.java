package com.pairing.negotiation.presentation.api.request;

import com.pairing.negotiation.domain.model.ConditionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 협상 중 마지노선 재설정 요청. (승인 패널의 "내 마지노선 [수정]")
 *
 * <p>형식은 {@link NegotiationStartRequest} 와 같다. 최초 제출과 같은 규칙으로 정규화·검증된다.
 * 다른 점은 <b>라운드를 올리지 않고 대리인도 돌리지 않는다</b>는 것뿐이다.
 *
 * <p>보낸 쟁점만 갱신한다. 한 건만 고치고 싶으면 그 한 건만 담으면 된다.
 */
@Schema(description = "마지노선 재설정 요청")
public record NegotiationFloorUpdateRequest(

        @Schema(description = "다시 그을 쟁점별 마지노선. 보낸 것만 갱신된다.")
        @NotEmpty(message = "마지노선은 1건 이상입니다.")
        @Valid
        List<Floor> conditions
) {

    @Schema(description = "쟁점별 마지노선")
    public record Floor(

            @Schema(description = "쟁점 종류. AMOUNT/PERIOD/START_DATE/WORK_STYLE/WORK_FORM/SCOPE/OTHER",
                    example = "AMOUNT")
            @NotNull(message = "쟁점 종류는 필수입니다.")
            ConditionType conditionType,

            @Schema(description = "마지노선 값(문자열). AMOUNT 는 원 단위 숫자(화면의 만 원 × 10000), "
                    + "PERIOD 는 \"4 MONTH\", WORK_STYLE/WORK_FORM 은 enum 코드, START_DATE 는 yyyy-MM-dd",
                    example = "4800000")
            @NotBlank(message = "마지노선 값은 필수입니다.")
            String value
    ) {
    }
}
