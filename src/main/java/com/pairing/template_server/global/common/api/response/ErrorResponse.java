package com.pairing.template_server.global.common.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "공통 에러 응답 데이터")
public record ErrorResponse(
        @Schema(description = "에러 발생 시각", example = "2026-05-21T07:09:00Z")
        Instant timestamp,

        @Schema(description = "HTTP 상태 코드", example = "404")
        int status,

        @Schema(description = "에러 분류 코드", example = "EX_001")
        String errorCode,

        @Schema(description = "에러 상세 메시지", example = "예시 데이터를 찾을 수 없습니다.")
        String message,

        @Schema(description = "에러 추적 ID (로그 확인용)", example = "a1b2c3d4")
        String traceId
) {}
