package com.pairing.account.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 카드 정보 수정. (마이페이지 &gt; 결제수단)
 *
 * <p>카드는 계정당 1개만 존재하며 가입 시 등록된 것을 고치는 것뿐이다. 신규 등록·삭제 API는 없다.
 * 번호는 하이픈을 넣어도 되고 서버가 숫자만 남겨 암호화 저장한다.
 */
@Schema(description = "카드 정보 수정 요청")
public record CardUpdateRequest(

        @Schema(description = "카드사", example = "신한카드")
        @NotBlank(message = "카드사는 필수입니다.")
        @Size(max = 30, message = "카드사는 30자를 넘을 수 없습니다.")
        String cardBrand,

        @Schema(description = "카드번호", example = "1234-5678-9123-4567")
        @NotBlank(message = "카드번호는 필수입니다.")
        @Pattern(regexp = "^[0-9-]{13,23}$", message = "카드번호 형식이 올바르지 않습니다.")
        String cardNumber,

        @Schema(description = "카드 소지자 이름", example = "홍길동")
        @NotBlank(message = "카드 소지자 이름은 필수입니다.")
        @Size(max = 50, message = "카드 소지자 이름은 50자를 넘을 수 없습니다.")
        String cardHolder
) {
}
