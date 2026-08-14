package com.pairing.client.presentation.api.request;

import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.account.presentation.api.request.AddressRequest;
import com.pairing.client.application.command.ClientProfileUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 클라이언트 기업정보 수정. (요구사항 R31)
 *
 * <p>사업자등록번호·사업 분야·담당자명(대표자명)·업무이메일은 수정할 수 없다.
 */
@Schema(description = "클라이언트 기업정보 수정 요청")
public record ClientProfileUpdateRequest(

        @Schema(description = "기업명", example = "주식회사 페어링")
        @NotBlank(message = "기업명은 필수입니다.")
        @Size(max = 100, message = "기업명은 100자 이하여야 합니다.")
        String companyName,

        @Schema(description = "직원수 구간", example = "SIZE_10_49")
        @NotNull(message = "직원수는 필수입니다.")
        EmployeeCount employeeCount,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        // @Pattern 은 null 을 통과시킨다(Bean Validation 명세). @NotBlank 가 함께 있어야 막힌다.
        @NotBlank(message = "전화번호는 필수입니다.")
        String phone,

        // 가입 때 필수인 값이라 수정에서도 필수다. 비울 수 있으면 계약서 갑 주소가 사라진다.
        @Schema(description = "회사 주소")
        @NotNull(message = "회사 주소는 필수입니다.")
        @Valid
        AddressRequest address,

        @Schema(description = "기업 로고 fileId. POST /api/v1/files?purpose=COMPANY_LOGO 로 먼저 올린다. "
                + "보내지 않으면 기존 로고를 그대로 둔다.", example = "7")
        Long logoFileId
) {

    public ClientProfileUpdateCommand toCommand(Long accountId) {
        return new ClientProfileUpdateCommand(accountId, companyName, employeeCount, phone,
                address.toAddress(), logoFileId);
    }
}
