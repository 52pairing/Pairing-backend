package com.pairing.auth.presentation.api.request;

import com.pairing.auth.application.command.FindEmailCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "아이디(이메일) 찾기 요청")
public record FindEmailRequest(

        @Schema(description = "이름", example = "홍길동")
        @NotBlank(message = "이름은 필수입니다.")
        String name,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phone
) {

    public FindEmailCommand toCommand() {
        return new FindEmailCommand(name, phone);
    }
}
