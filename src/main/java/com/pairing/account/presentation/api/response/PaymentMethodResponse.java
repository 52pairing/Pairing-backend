package com.pairing.account.presentation.api.response;

import com.pairing.account.domain.model.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 결제수단 한 건. 원본 번호는 절대 내려가지 않는다.
 *
 * <p>계정당 카드 1개(수수료 결제) + 계좌 1개(용역비 수령)만 존재한다. 목록은 항상 이 두 건이다.
 * {@code displayName} 은 화면에 그대로 찍을 수 있는 문자열이다.
 * 카드면 "신한카드 **** 1234", 계좌면 "신한은행 **** 6789".
 */
@Schema(description = "결제수단")
public record PaymentMethodResponse(

        @Schema(description = "결제수단 ID", example = "300")
        Long paymentMethodId,

        @Schema(description = "결제수단 종류", example = "CARD")
        PaymentMethodType methodType,

        @Schema(description = "화면 표시용 이름", example = "신한카드 **** 1234")
        String displayName,

        @Schema(description = "카드사. 계좌면 null", example = "신한카드")
        String cardBrand,

        @Schema(description = "카드번호 끝 4자리. 계좌면 null", example = "1234")
        String cardLast4,

        @Schema(description = "카드 소지자 이름. 계좌면 null", example = "김개발")
        String cardHolder,

        @Schema(description = "은행명. 카드면 null", example = "신한은행")
        String bankName,

        @Schema(description = "계좌번호 끝 4자리. 카드면 null", example = "6789")
        String accountLast4,

        @Schema(description = "예금주. 카드면 null", example = "김개발")
        String accountHolder
) {
}
