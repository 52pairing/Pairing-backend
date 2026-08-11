package com.pairing.freelancer.presentation.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 이력서 임시 저장. (요구사항 R21)
 *
 * <p>서버는 {@code payload} 안을 들여다보지 않는다. 절반만 채운 상태로도 저장되는 게 이 기능의
 * 목적이라, 필드 검증은 정식 등록({@code PUT /me/resume})에서만 한다.
 *
 * <p>화면 입력값을 넣고 싶은 대로 넣으면 되고, 복원할 때 넣은 그대로 돌려받는다.
 * 위저드 1단계(희망 조건)와 2단계(이력서)를 한 덩어리에 같이 담아도 된다.
 */
@Schema(description = "이력서 임시 저장 요청")
public record ResumeDraftRequest(

        @Schema(description = "화면 입력값 전체. 형식은 프론트가 정한다.",
                example = """
                        {"condition":{"jobCategory":"DEVELOPMENT"},"resume":{"selfIntroduction":"작성 중..."}}""")
        @NotNull(message = "임시 저장할 내용은 필수입니다.")
        JsonNode payload
) {
}
