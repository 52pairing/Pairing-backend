package com.pairing.auth.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "소셜 인가 URL 응답")
public record AuthorizeUrlResponse(

        @Schema(description = "이 URL로 이동시키면 공급자 로그인 화면이 열린다.")
        String authorizeUrl,

        @Schema(description = "콜백에서 그대로 돌려줘야 하는 state")
        String state
) {
}
