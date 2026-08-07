package com.pairing.settlement.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 수수료·위약금 결제 요청. (결제 수단 선택 모달)
 *
 * <p>결제 모달에서 고른 결제수단 ID 를 보낸다. 마이페이지에서 등록한 카드·간편결제 중 하나여야 한다.
 */
@Schema(description = "결제 요청")
public record SettlementPayRequest(

        @Schema(description = "결제수단 ID", example = "300")
        @NotNull(message = "결제수단을 선택해 주세요.")
        Long paymentMethodId
) {
}
