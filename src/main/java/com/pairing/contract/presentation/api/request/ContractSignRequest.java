package com.pairing.contract.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

/**
 * 계약서 서명. 양측이 모두 서명해야 계약이 확정된다.
 *
 * <p>서명 이미지는 <b>선택</b>이다. 없으면 동의 클릭만으로 서명 처리하고, 있으면 그 이미지를 함께
 * 증거로 남긴다. 화면의 서명 패드가 붙기 전에도 서명 흐름이 막히지 않게 하려는 것이다.
 * 이미지를 반드시 받기로 하면 {@code @NotNull} 을 붙이면 된다.
 *
 * <p>서명 기한은 없다. 요구사항 44행이 "계약서 생성 후 서명 기한은 무기한" 이라 자동 취소도 하지 않는다.
 */
@Schema(description = "계약 서명 요청")
public record ContractSignRequest(

        @Schema(description = "계약 내용 확인 동의", example = "true")
        @AssertTrue(message = "계약 내용에 동의해야 서명할 수 있습니다.")
        boolean agreed,

        @Schema(description = "서명 이미지 fileId. purpose=SIGNATURE 로 업로드한 값. 없으면 동의만으로 서명",
                example = "123")
        Long signatureFileId
) {
}
