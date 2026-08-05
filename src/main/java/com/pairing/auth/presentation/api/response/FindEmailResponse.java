package com.pairing.auth.presentation.api.response;

import com.pairing.auth.application.result.MaskedEmailResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 아이디 찾기 응답.
 *
 * <p>같은 이름·전화번호로 클라이언트와 프리랜서 계정을 각각 가질 수 있어 목록으로 내려준다.
 */
@Schema(description = "아이디 찾기 응답")
public record FindEmailResponse(

        @Schema(description = "찾은 계정 목록")
        List<Item> accounts
) {

    @Schema(description = "마스킹된 계정 정보")
    public record Item(

            @Schema(description = "역할", example = "FREELANCER")
            String role,

            @Schema(description = "마스킹된 이메일", example = "ho*****@gmail.com")
            String maskedEmail
    ) {
    }

    public static FindEmailResponse from(List<MaskedEmailResult> results) {
        return new FindEmailResponse(results.stream()
                .map(result -> new Item(result.role().name(), result.maskedEmail()))
                .toList());
    }
}
