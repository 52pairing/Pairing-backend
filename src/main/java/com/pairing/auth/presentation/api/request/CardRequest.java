package com.pairing.auth.presentation.api.request;

import com.pairing.account.application.command.CardCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 수수료 결제용 카드. 하이픈은 있어도 되고, 서버가 숫자만 남겨 암호화 저장한다. */
@Schema(description = "카드 등록 요청")
public record CardRequest(

        @Schema(description = "카드번호. 하이픈 허용", example = "1234-5678-1234-5678")
        @NotBlank(message = "카드번호는 필수입니다.")
        @Pattern(regexp = "^[0-9][0-9-\\s]{10,24}$", message = "카드번호 형식이 올바르지 않습니다.")
        String cardNumber,

        @Schema(description = "카드사", example = "신한카드")
        @NotBlank(message = "카드사는 필수입니다.")
        @Size(max = 30, message = "카드사는 30자 이하여야 합니다.")
        String cardBrand
) {

    /** 가입 화면은 카드 소지자명을 받지 않는다. 마이페이지에서 카드를 수정할 때 채워진다. */
    public CardCommand toCommand() {
        return new CardCommand(cardNumber, cardBrand, null);
    }
}
