package com.pairing.contract.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 계약서 서명 거부. 거부하면 상대에게 알림이 가고 협상이 종료된다. */
@Schema(description = "계약 서명 거부 요청")
public record ContractRejectRequest(

        @Schema(description = "거부 사유", example = "업무 범위가 협의 내용과 다릅니다.")
        @NotBlank(message = "거부 사유는 필수입니다.")
        @Size(max = 255, message = "사유는 255자 이하여야 합니다.")
        String reason
) {
}
