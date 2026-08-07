package com.pairing.meta.presentation.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 선택 목록 항목. code 를 저장/전송하고 label 을 화면에 보여준다. */
@Schema(description = "코드 목록 항목")
public record CodeItemResponse(

        @Schema(description = "코드", example = "BACKEND")
        String code,

        @Schema(description = "표시명", example = "백엔드 개발자")
        String label,

        @Schema(description = "상위 코드. 직무는 직군을 가리킨다.", example = "DEVELOPMENT")
        String parentCode
) {

    public static CodeItemResponse of(String code, String label) {
        return new CodeItemResponse(code, label, null);
    }

    public static CodeItemResponse of(String code, String label, String parentCode) {
        return new CodeItemResponse(code, label, parentCode);
    }
}
