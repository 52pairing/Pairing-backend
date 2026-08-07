package com.pairing.client.presentation.api.request;

import com.pairing.account.domain.model.EmployeeCount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 클라이언트 계정 정보 수정. (요구사항 R31)
 *
 * <p>사업자등록번호·사업 분야·업무 이메일·대표자명은 수정할 수 없다.
 * 수정 전에 이메일 인증(purpose=PROFILE_UPDATE)을 마쳐야 한다.
 * 인증 여부는 서버가 확인하므로 요청에 인증코드를 담지 않는다.
 */
@Schema(description = "클라이언트 정보 수정 요청")
public record ClientProfileUpdateRequest(

        @Schema(description = "기업명", example = "주식회사 페어링")
        @NotBlank(message = "기업명은 필수입니다.")
        @Size(max = 100, message = "기업명은 100자 이하여야 합니다.")
        String companyName,

        @Schema(description = "직원수 구간", example = "SIZE_10_49")
        @NotNull(message = "직원수는 필수입니다.")
        EmployeeCount employeeCount,

        @Schema(description = "휴대폰번호(법인폰)", example = "010-1234-5678")
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phone,

        @Schema(description = "기업 로고 fileId", example = "3")
        Long logoFileId,

        @Schema(description = "주소", example = "서울 강남구")
        @Size(max = 255)
        String address
) {
}
