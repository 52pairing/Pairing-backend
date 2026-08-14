package com.pairing.auth.presentation.api.request;

import com.pairing.account.application.command.CardCommand;
import com.pairing.account.domain.model.CardCompany;
import com.pairing.account.domain.model.PaymentNumberFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 수수료 결제용 카드. 하이픈은 있어도 되고, 서버가 숫자만 남겨 암호화 저장한다. */
@Schema(description = "카드 등록 요청")
public record CardRequest(

        @Schema(description = "카드번호. 숫자 16자리, 하이픈·공백 허용", example = "1234-5678-1234-5678")
        @NotBlank(message = "카드번호는 필수입니다.")
        @Pattern(regexp = PaymentNumberFormat.CARD_NUMBER_REGEX,
                message = PaymentNumberFormat.CARD_NUMBER_MESSAGE)
        String cardNumber,

        // 한글 카드사명("신한카드")이 아니라 enum 이름("SHINHAN")을 보낸다.
        @Schema(description = "카드사. GET /api/v1/meta/card-companies 의 code 를 그대로 보낸다.",
                example = "SHINHAN")
        @NotNull(message = "카드사는 필수입니다.")
        CardCompany cardBrand
) {

    /** 가입 화면은 카드 소지자명을 받지 않는다. 마이페이지에서 카드를 수정할 때 채워진다. */
    public CardCommand toCommand() {
        return new CardCommand(cardNumber, cardBrand, null);
    }
}
