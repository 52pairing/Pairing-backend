package com.pairing.contract.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

/**
 * 계약서 서명.
 *
 * <p>서명은 별도 이미지 업로드 없이 전자 서명 동의로 처리한다. 화면은 확인 모달 하나뿐이다.
 * 양측이 모두 서명해야 계약이 확정된다. 서명 기한까지 서명하지 않으면 계약이 자동 취소될 수 있다.
 */
@Schema(description = "계약 서명 요청")
public record ContractSignRequest(

        @Schema(description = "계약 내용 확인 동의", example = "true")
        @AssertTrue(message = "계약 내용에 동의해야 서명할 수 있습니다.")
        boolean agreed
) {
}
