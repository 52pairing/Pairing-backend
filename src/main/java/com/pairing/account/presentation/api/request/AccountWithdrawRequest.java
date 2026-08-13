package com.pairing.account.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원 탈퇴. (요구사항 R17, R31)
 *
 * <p>진행 중인 프로젝트가 있거나 미납 요금이 있으면 탈퇴할 수 없다.
 * 탈퇴해도 계정 행은 지우지 않는다. 리뷰·계약·정산이 참조하고 있어서다.
 * 이메일은 더미값으로 바꾸고 원본은 해시로만 남겨 30일 재가입 제한을 판정한다.
 *
 * <p>본인 확인은 비밀번호가 아니라 <b>동의 체크 + 확인 문구 입력</b>으로 한다. 이미 로그인된
 * 세션에서만 부를 수 있는 API 라 비밀번호를 또 받아도 확인되는 게 없고, 소셜 전용 계정은
 * 비밀번호 자체가 없어 같은 흐름을 태울 수 없다.
 */
@Schema(description = "회원 탈퇴 요청")
public record AccountWithdrawRequest(

        @Schema(description = "안내 사항 확인 및 데이터 삭제 동의. 반드시 true", example = "true")
        @AssertTrue(message = "탈퇴 안내에 동의해야 합니다.")
        Boolean agreed,

        @Schema(description = "확인 문구. \"탈퇴하겠습니다\" 를 정확히 입력해야 한다.",
                example = "탈퇴하겠습니다")
        @NotBlank(message = "확인 문구를 입력해 주세요.")
        String confirmText,

        @Schema(description = "탈퇴 사유(선택)", example = "서비스를 더 이용하지 않습니다.")
        @Size(max = 500, message = "사유는 500자 이하여야 합니다.")
        String reason
) {
}
