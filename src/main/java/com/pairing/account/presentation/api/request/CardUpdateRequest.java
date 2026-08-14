package com.pairing.account.presentation.api.request;

import com.pairing.account.application.command.CardCommand;
import com.pairing.account.domain.model.CardCompany;
import com.pairing.account.domain.model.PaymentNumberFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 카드 정보 수정. (마이페이지 &gt; 결제수단)
 *
 * <p>카드는 계정당 1개만 존재하며 가입 시 등록된 것을 고치는 것뿐이다. 신규 등록·삭제 API는 없다.
 * 번호는 하이픈을 넣어도 되고 서버가 숫자만 남겨 암호화 저장한다.
 *
 * <p>형식은 가입({@code CardRequest})과 같은 규칙을 쓴다. 두 곳이 갈리면 가입은 통과한 카드가
 * 수정에서 막힌다.
 */
@Schema(description = "카드 정보 수정 요청")
public record CardUpdateRequest(

        // 한글 카드사명("신한카드")이 아니라 enum 이름("SHINHAN")을 보낸다.
        @Schema(description = "카드사. GET /api/v1/meta/card-companies 의 code 를 그대로 보낸다.",
                example = "SHINHAN")
        @NotNull(message = "카드사는 필수입니다.")
        CardCompany cardBrand,

        @Schema(description = "카드번호. 숫자 16자리, 하이픈·공백 허용", example = "1234-5678-9123-4567")
        @NotBlank(message = "카드번호는 필수입니다.")
        @Pattern(regexp = PaymentNumberFormat.CARD_NUMBER_REGEX,
                message = PaymentNumberFormat.CARD_NUMBER_MESSAGE)
        String cardNumber,

        @Schema(description = "카드 소지자 이름", example = "홍길동")
        @NotBlank(message = "카드 소지자 이름은 필수입니다.")
        @Size(max = 50, message = "카드 소지자 이름은 50자를 넘을 수 없습니다.")
        String cardHolder
) {

    public CardCommand toCommand() {
        return new CardCommand(cardNumber, cardBrand, cardHolder);
    }
}
