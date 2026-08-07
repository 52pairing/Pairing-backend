package com.pairing.contract.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 계약 중도 파기. (요구사항 R30)
 *
 * <p>파기 주체는 수행분 정산과 별도로 상대방 10% · 플랫폼 10%의 위약금을 부담한다.
 * 위약금 기준이 되는 수행분 금액은 양측 확인이 필요하므로 요청에 담는다.
 */
@Schema(description = "계약 중도 파기 요청")
public record ContractTerminateRequest(

        @Schema(description = "파기 사유", example = "내부 사정으로 프로젝트를 중단합니다.")
        @NotBlank(message = "파기 사유는 필수입니다.")
        @Size(max = 255, message = "사유는 255자 이하여야 합니다.")
        String reason,

        @Schema(description = "수행분 보수(원). 위약금 계산 기준", example = "10000000")
        @NotNull(message = "수행분 보수는 필수입니다.")
        @Min(value = 0, message = "0 이상이어야 합니다.")
        Long workedAmount
) {
}
