package com.pairing.auth.presentation.api.request;

import com.pairing.account.application.command.PaymentMethodCommand;
import com.pairing.account.domain.model.PaymentMethodType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 카드(수수료 결제) 또는 계좌(용역비 수령) 하나. 가입 시 두 종류를 모두 등록한다. */
@Schema(description = "결제/정산 수단 등록 요청")
public record PaymentMethodRequest(

        @Schema(description = "수단 종류", example = "CARD")
        @NotNull(message = "수단 종류는 필수입니다.")
        PaymentMethodType methodType,

        @Schema(description = "카드번호(숫자만). methodType이 CARD일 때 필수", example = "1234567812345678")
        @Pattern(regexp = "^$|^\\d{12,19}$", message = "카드번호 형식이 올바르지 않습니다.")
        String cardNumber,

        @Schema(description = "카드사명", example = "신한카드")
        String cardBrand,

        @Schema(description = "은행 코드. methodType이 BANK_ACCOUNT일 때 필수", example = "088")
        String bankCode,

        @Schema(description = "계좌번호(숫자만)", example = "11012345678901")
        @Pattern(regexp = "^$|^\\d{6,20}$", message = "계좌번호 형식이 올바르지 않습니다.")
        String accountNo,

        @Schema(description = "예금주", example = "홍길동")
        String accountHolder
) {

    public PaymentMethodCommand toCommand() {
        return new PaymentMethodCommand(methodType, cardNumber, cardBrand, bankCode, accountNo, accountHolder);
    }
}
