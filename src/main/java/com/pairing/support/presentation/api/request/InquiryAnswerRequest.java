package com.pairing.support.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** [관리자] 1:1 문의 답변. 답변하면 상태가 ANSWERED 로 바뀌고 사용자에게 알림이 간다. */
@Schema(description = "1:1 문의 답변 요청")
public record InquiryAnswerRequest(

        @Schema(description = "답변 내용")
        @NotBlank(message = "답변은 필수입니다.")
        @Size(max = 2000, message = "답변은 2000자 이하여야 합니다.")
        String answer
) {
}
