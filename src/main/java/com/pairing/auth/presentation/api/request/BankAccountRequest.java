package com.pairing.auth.presentation.api.request;

import com.pairing.account.application.command.BankAccountCommand;
import com.pairing.account.domain.model.PaymentNumberFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 용역비 수령용 계좌. 은행 목록은 {@code GET /api/v1/meta/banks} 로 받는다. */
@Schema(description = "계좌 등록 요청")
public record BankAccountRequest(

        @Schema(description = "은행 코드(금융결제원 기관코드)", example = "088")
        @Pattern(regexp = "^\\d{3,4}$", message = "은행 코드 형식이 올바르지 않습니다.")
        @NotBlank(message = "은행은 필수입니다.")
        String bankCode,

        @Schema(description = "계좌번호. 숫자 10~14자리, 하이픈·공백 허용", example = "110-123-456789")
        @NotBlank(message = "계좌번호는 필수입니다.")
        @Pattern(regexp = PaymentNumberFormat.ACCOUNT_NO_REGEX,
                message = PaymentNumberFormat.ACCOUNT_NO_MESSAGE)
        String accountNo,

        @Schema(description = "예금주", example = "홍길동")
        @NotBlank(message = "예금주는 필수입니다.")
        @Size(max = 50, message = "예금주는 50자 이하여야 합니다.")
        String accountHolder
) {

    public BankAccountCommand toCommand() {
        return new BankAccountCommand(bankCode, accountNo, accountHolder);
    }
}
