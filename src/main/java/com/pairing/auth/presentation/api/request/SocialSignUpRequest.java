package com.pairing.auth.presentation.api.request;

import com.pairing.account.presentation.api.request.AddressRequest;
import com.pairing.auth.application.command.SocialSignUpCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * 프리랜서 소셜 회원가입 요청.
 *
 * <p>이메일 필드가 없다. 공급자가 준 값을 티켓에서 꺼내 쓰므로 사용자가 바꿀 수 없다.
 */
@Schema(description = "프리랜서 소셜 회원가입 요청")
public record SocialSignUpRequest(

        @Schema(description = "소셜 콜백에서 받은 가입 티켓")
        @NotBlank(message = "가입 티켓은 필수입니다.")
        String signUpTicket,

        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        // @Pattern 은 null 을 통과시킨다(Bean Validation 명세). @NotBlank 가 함께 있어야 막힌다.
        @NotBlank(message = "전화번호는 필수입니다.")
        String phone,

        @Schema(description = "생년월일(만 18세 이상)", example = "1995-03-01")
        @NotNull(message = "생년월일은 필수입니다.")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate birthDate,

        // 주소 찾기 위젯이 준 조각을 그대로 보낸다. 합쳐서 보내면 수정 화면에서 다시 나눌 수 없다.
        @Schema(description = "주소")
        @NotNull(message = "주소는 필수입니다.")
        @Valid
        AddressRequest address,

        @Schema(description = "수수료 결제용 카드")
        @NotNull(message = "카드 정보는 필수입니다.")
        @Valid
        CardRequest card,

        @Schema(description = "용역비 수령용 계좌")
        @NotNull(message = "계좌 정보는 필수입니다.")
        @Valid
        BankAccountRequest bankAccount,

        @Schema(description = "약관 동의 목록")
        @NotEmpty(message = "약관 동의는 필수입니다.")
        @Valid
        List<TermsAgreementRequest> agreements
) {

    public SocialSignUpCommand toCommand(String userAgent) {
        return new SocialSignUpCommand(
                signUpTicket,
                name,
                phone,
                birthDate,
                address.toAddress(),
                card.toCommand(),
                bankAccount.toCommand(),
                agreements.stream().map(TermsAgreementRequest::toCommand).toList(),
                userAgent
        );
    }
}
