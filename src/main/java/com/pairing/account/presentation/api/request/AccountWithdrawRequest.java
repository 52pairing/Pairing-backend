package com.pairing.account.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴. (요구사항 R17, R31)
 *
 * <p>진행 중인 프로젝트가 있거나 미납 요금이 있으면 탈퇴할 수 없다.
 * 탈퇴해도 계정 행은 지우지 않는다. 리뷰·계약·정산이 참조하고 있어서다.
 * 이메일은 더미값으로 바꾸고 원본은 해시로만 남겨 30일 재가입 제한을 판정한다.
 */
@Schema(description = "회원 탈퇴 요청")
public record AccountWithdrawRequest(

        @Schema(description = "본인 확인용 현재 비밀번호. 소셜 전용 계정은 비운다.")
        @Size(max = 20)
        String currentPassword,

        @Schema(description = "탈퇴 사유(선택)", example = "서비스를 더 이용하지 않습니다.")
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
        String reason
) {
}
