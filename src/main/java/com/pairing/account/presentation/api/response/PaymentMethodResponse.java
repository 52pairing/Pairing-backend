package com.pairing.account.presentation.api.response;

import com.pairing.account.domain.model.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 결제수단 한 건. 원본 번호는 절대 내려가지 않는다.
 *
 * <p>{@code displayName} 은 화면에 그대로 찍을 수 있는 문자열이다.
 * 카드면 "신한카드 **** 1234", 간편결제면 "카카오페이 · 계좌 연동".
 */
@Schema(description = "결제수단")
public record PaymentMethodResponse(

        @Schema(description = "결제수단 ID", example = "300")
        Long paymentMethodId,

        @Schema(description = "결제수단 종류", example = "CARD")
        PaymentMethodType methodType,

        @Schema(description = "화면 표시용 이름", example = "신한카드 **** 1234")
        String displayName,

        @Schema(description = "카드사. 간편결제면 null", example = "신한카드")
        String cardBrand,

        @Schema(description = "카드번호 끝 4자리. 간편결제면 null", example = "1234")
        String cardLast4,

        @Schema(description = "유효기간 MM/YY. 간편결제면 null", example = "09/28")
        String expiry,

        @Schema(description = "카드 소지자 이름. 간편결제면 null", example = "김개발")
        String cardHolder,

        @Schema(description = "기본 결제수단 여부", example = "true")
        boolean isDefault
) {
}
