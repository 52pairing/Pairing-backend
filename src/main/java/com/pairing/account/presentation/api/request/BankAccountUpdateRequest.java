package com.pairing.account.presentation.api.request;

import com.pairing.account.application.command.BankAccountCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 계좌 정보 수정. (마이페이지 &gt; 결제수단)
 *
 * <p>용역비 수령 계좌는 계정당 1개만 존재하며 가입 시 등록된 것을 고치는 것뿐이다. 신규 등록·삭제 API는 없다.
 */
@Schema(description = "계좌 정보 수정 요청")
public record BankAccountUpdateRequest(

        // 금융결제원 기관코드(숫자 3자리)다. enum 이름("SHINHAN")을 보내면 AC_006 으로 막힌다.
        @Schema(description = "은행 코드(금융결제원 기관코드 3자리). GET /api/v1/meta/banks 참고", example = "088")
        @NotBlank(message = "은행은 필수입니다.")
        String bankCode,

        @Schema(description = "계좌번호", example = "110-123-456789")
        @NotBlank(message = "계좌번호는 필수입니다.")
        @Size(max = 30, message = "계좌번호가 너무 깁니다.")
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
