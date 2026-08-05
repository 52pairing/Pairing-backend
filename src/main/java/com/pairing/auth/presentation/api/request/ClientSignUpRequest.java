package com.pairing.auth.presentation.api.request;

import com.pairing.account.domain.model.BusinessField;
import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.auth.application.command.ClientSignUpCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 클라이언트(국내 기업) 회원가입 요청. 이메일 인증을 마친 뒤 호출한다. */
@Schema(description = "클라이언트 회원가입 요청")
public record ClientSignUpRequest(

        @Schema(description = "기업명", example = "주식회사 페어링")
        @NotBlank(message = "기업명은 필수입니다.")
        @Size(max = 100, message = "기업명은 100자 이하여야 합니다.")
        String companyName,

        @Schema(description = "사업자등록번호(하이픈 없이 숫자 10자리)", example = "1234567890")
        @Pattern(regexp = "^\\d{10}$", message = "사업자등록번호는 하이픈 없이 숫자 10자리입니다.")
        String businessNo,

        @Schema(description = "사업 분야", example = "IT_CONTENTS_AI")
        @NotNull(message = "사업 분야는 필수입니다.")
        BusinessField businessField,

        @Schema(description = "직원수 구간", example = "SIZE_10_49")
        @NotNull(message = "직원수는 필수입니다.")
        EmployeeCount employeeCount,

        @Schema(description = "업무 이메일(로그인 아이디)", example = "owner@pairing.com")
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "대표자명", example = "홍길동")
        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 50자 이하여야 합니다.")
        String name,

        @Schema(description = "휴대폰번호(법인폰)", example = "010-1234-5678")
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phone,

        @Schema(description = "비밀번호(대소문자+숫자+특수문자, 8~20자)", example = "Passw0rd!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password,

        @Schema(description = "비밀번호 확인", example = "Passw0rd!")
        @NotBlank(message = "비밀번호 확인은 필수입니다.")
        String passwordConfirm,

        @Schema(description = "결제/정산 수단(카드, 계좌)")
        @NotEmpty(message = "결제 수단은 필수입니다.")
        @Valid
        List<PaymentMethodRequest> paymentMethods,

        @Schema(description = "약관 동의 목록")
        @NotEmpty(message = "약관 동의는 필수입니다.")
        @Valid
        List<TermsAgreementRequest> agreements
) {

    public ClientSignUpCommand toCommand(String userAgent) {
        return new ClientSignUpCommand(
                email,
                password,
                passwordConfirm,
                name,
                phone,
                companyName,
                businessNo,
                businessField,
                employeeCount,
                paymentMethods.stream().map(PaymentMethodRequest::toCommand).toList(),
                agreements.stream().map(TermsAgreementRequest::toCommand).toList(),
                userAgent
        );
    }
}
