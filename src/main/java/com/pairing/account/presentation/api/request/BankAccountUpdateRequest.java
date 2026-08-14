package com.pairing.account.presentation.api.request;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.domain.model.PaymentNumberFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 계좌 정보 수정. (마이페이지 &gt; 결제수단)
 *
 * <p>용역비 수령 계좌는 계정당 1개만 존재하며 가입 시 등록된 것을 고치는 것뿐이다. 신규 등록·삭제 API는 없다.
 */
@Schema(description = "계좌 정보 수정 요청")
public record BankAccountUpdateRequest(

        // 금융결제원 기관코드(숫자)다. 형식 검사는 가입(BankAccountRequest)과 같은 규칙을 쓴다.
        // enum 이름("SHINHAN")은 여기서 걸리고, 형식은 맞지만 목록에 없는 코드("999")는
        // 저장 직전에 AC_006 으로 막힌다.
        @Schema(description = "은행 코드(금융결제원 기관코드 3자리). GET /api/v1/meta/banks 참고", example = "088")
        @NotBlank(message = "은행은 필수입니다.")
        @Pattern(regexp = "^\\d{3,4}$", message = "은행 코드 형식이 올바르지 않습니다.")
        String bankCode,

        // 형식은 가입(BankAccountRequest)과 같은 규칙을 쓴다. 갈리면 가입은 통과한 계좌가 수정에서 막힌다.
        @Schema(description = "계좌번호. 숫자 10~14자리, 하이픈·공백 허용", example = "110-123-456789")
        @NotBlank(message = "계좌번호는 필수입니다.")
        @Pattern(regexp = PaymentNumberFormat.ACCOUNT_NO_REGEX,
                message = PaymentNumberFormat.ACCOUNT_NO_MESSAGE)
        String accountNo,

        @Schema(description = "예금주", example = "홍길동")
        @NotBlank(message = "예금주는 필수입니다.")
        @Size(max = 50, message = "예금주는 50자를 넘을 수 없습니다.")
        String accountHolder
) {

    public BankAccountCommand toCommand() {
        return new BankAccountCommand(bankCode, accountNo, accountHolder);
    }
}
