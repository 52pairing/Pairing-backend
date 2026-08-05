package com.pairing.account.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 화면 선택 목록용 코드/라벨 쌍. 프론트는 code를 저장하고 label을 보여준다. */
@Schema(description = "코드 목록 항목")
public record CodeLabelResponse(

        @Schema(description = "저장/전송에 사용하는 코드", example = "IT_CONTENTS_AI")
        String code,

        @Schema(description = "화면에 표시하는 이름", example = "IT/컨텐츠/AI")
        String label
) {
}
