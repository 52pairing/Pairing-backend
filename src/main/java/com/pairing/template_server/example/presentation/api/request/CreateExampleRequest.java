package com.pairing.template_server.example.presentation.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "예시 데이터 생성 요청")
public record CreateExampleRequest(
        @Schema(description = "예시 이름", example = "예시 데이터")
        @NotBlank(message = "이름은 필수입니다.")
        String name
) {
}