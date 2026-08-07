package com.pairing.account.presentation.api.request;

import com.pairing.account.domain.model.EasyPayProvider;
import com.pairing.account.domain.model.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 결제수단 등록. (마이페이지 &gt; 결제수단 &gt; 결제수단 추가)
 *
 * <p>수수료 결제용이다. 계정당 최대 3개까지 등록할 수 있고, 첫 등록분이 기본 결제수단이 된다.
 * 용역비 수령 계좌는 가입 시 한 번만 받으며 여기서 다루지 않는다.
 *
 * <p>{@code methodType} 에 따라 채워야 하는 하위 객체가 다르다.
 * CARD 면 {@code card}, EASY_PAY 면 {@code easyPay} 가 필요하다.
 */
@Schema(description = "결제수단 등록 요청")
public record PaymentMethodCreateRequest(

        @Schema(description = "결제수단 종류", example = "CARD")
        @NotNull(message = "결제수단 종류는 필수입니다.")
        PaymentMethodType methodType,

        @Schema(description = "카드 정보. methodType 이 CARD 일 때 필수")
        @Valid Card card,

        @Schema(description = "간편결제 정보. methodType 이 EASY_PAY 일 때 필수")
        @Valid EasyPay easyPay
) {

    /** 카드 정보. 번호는 하이픈을 넣어도 되고 서버가 숫자만 남겨 암호화 저장한다. */
    @Schema(description = "카드 정보")
    public record Card(

            @Schema(description = "카드사", example = "신한카드")
            @NotBlank(message = "카드사는 필수입니다.")
            @Size(max = 30, message = "카드사는 30자를 넘을 수 없습니다.")
            String cardBrand,

            @Schema(description = "카드번호", example = "1234-5678-9123-4567")
            @NotBlank(message = "카드번호는 필수입니다.")
            @Pattern(regexp = "^[0-9-]{13,23}$", message = "카드번호 형식이 올바르지 않습니다.")
            String cardNumber,

            @Schema(description = "유효기간 월", example = "9")
            @NotNull(message = "유효기간은 필수입니다.")
            @Min(value = 1, message = "유효기간 월이 올바르지 않습니다.")
            @Max(value = 12, message = "유효기간 월이 올바르지 않습니다.")
            Integer expiryMonth,

            @Schema(description = "유효기간 연도 두 자리", example = "28")
            @NotNull(message = "유효기간은 필수입니다.")
            @Min(value = 0, message = "유효기간 연도가 올바르지 않습니다.")
            @Max(value = 99, message = "유효기간 연도가 올바르지 않습니다.")
            Integer expiryYear,

            // CVC 는 저장하지 않는다. 등록 시점 검증에만 쓰고 버린다.
            @Schema(description = "CVC. 저장하지 않고 검증에만 사용한다", example = "123")
            @NotBlank(message = "CVC는 필수입니다.")
            @Pattern(regexp = "^[0-9]{3,4}$", message = "CVC 형식이 올바르지 않습니다.")
            String cvc,

            @Schema(description = "카드 소지자 이름", example = "홍길동")
            @NotBlank(message = "카드 소지자 이름은 필수입니다.")
            @Size(max = 50, message = "카드 소지자 이름은 50자를 넘을 수 없습니다.")
            String cardHolder
    ) {
    }

    /** 간편결제 정보. */
    @Schema(description = "간편결제 정보")
    public record EasyPay(

            @Schema(description = "간편결제 공급자", example = "KAKAO_PAY")
            @NotNull(message = "간편결제 공급자는 필수입니다.")
            EasyPayProvider provider
    ) {
    }
}
