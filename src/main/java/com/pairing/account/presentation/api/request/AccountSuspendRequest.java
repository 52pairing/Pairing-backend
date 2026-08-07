package com.pairing.account.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * [관리자] 회원 정지. (요구사항 R37)
 *
 * <p>정지되면 로그인할 수 없다. 정지 상태는 컬럼이 아니라 Redis 에서 관리한다.
 */
@Schema(description = "회원 정지 요청")
public record AccountSuspendRequest(

        @Schema(description = "정지 사유. 사용자에게 안내된다.", example = "허위 정보 등록")
        @NotBlank(message = "정지 사유는 필수입니다.")
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
        String reason,

        @Schema(description = "정지 기간(일). 비우면 무기한", example = "30")
        Integer days
) {
}
