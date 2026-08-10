package com.pairing.client.presentation.api.request;

import com.pairing.account.domain.model.EmployeeCount;
import com.pairing.client.application.command.ClientProfileUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
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
        String phone,

        @Schema(description = "주소", example = "서울 강남구")
        @Size(max = 255)
        String address
) {

    public ClientProfileUpdateCommand toCommand(Long accountId) {
        return new ClientProfileUpdateCommand(accountId, companyName, employeeCount, phone, address);
    }
}
