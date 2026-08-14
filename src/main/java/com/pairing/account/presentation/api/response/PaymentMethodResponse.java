package com.pairing.account.presentation.api.response;

import com.pairing.account.domain.model.PaymentMethod;
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

        @Schema(description = "카드사명(화면 표시용). 계좌면 null", example = "신한카드")
        String cardBrand,

        // 수정 폼의 카드사 select 초기값으로 쓴다. cardBrand(한글명)로는 항목을 고를 수 없다.
        @Schema(description = "카드사 코드. GET /api/v1/meta/card-companies 의 code 와 같다. 계좌면 null",
                example = "SHINHAN")
        String cardCompany,

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

    /**
     * 도메인 -&gt; 응답. 종류에 맞지 않는 필드는 null 로 비운다(카드 응답에 계좌 필드가 섞이면 화면이 헷갈린다).
     *
     * <p>{@code cardHolder} 는 가입 요청에 없는 값이라, 마이페이지에서 카드를 한 번 수정하기 전까지는
     * 기존 계정에서 null 로 내려간다.
     */
    public static PaymentMethodResponse from(PaymentMethod paymentMethod) {
        boolean card = paymentMethod.isCard();

        return new PaymentMethodResponse(
                paymentMethod.getId(),
                paymentMethod.getMethodType(),
                paymentMethod.getDisplayName(),
                card ? paymentMethod.getCardBrandName() : null,
                card ? paymentMethod.getCardBrand() : null,
                card ? paymentMethod.getCardLast4() : null,
                card ? paymentMethod.getCardHolder() : null,
                card ? null : paymentMethod.getBankName(),
                card ? null : paymentMethod.getAccountLast4(),
                card ? null : paymentMethod.getAccountHolder());
    }
}
