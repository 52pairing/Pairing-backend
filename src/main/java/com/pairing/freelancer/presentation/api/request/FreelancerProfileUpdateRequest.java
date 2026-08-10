package com.pairing.freelancer.presentation.api.request;

import com.pairing.freelancer.application.command.FreelancerProfileUpdateCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 프리랜서 계정 정보 수정. (요구사항 R17)
 *
 * <p>이름과 생년월일은 수정할 수 없다. 비밀번호 변경은 {@code PATCH /api/v1/auth/password} 를 쓴다.
 * 수정 전에 {@code POST /api/v1/auth/email-verifications}(purpose=PROFILE_UPDATE)로 이메일 인증을
 * 먼저 마쳐야 한다.
 */
@Schema(description = "프리랜서 정보 수정 요청")
public record FreelancerProfileUpdateRequest(

        @Schema(description = "프로필 사진 fileId", example = "3")
        Long profileFileId,

        @Schema(description = "전화번호", example = "010-1234-5678")
        @Pattern(regexp = "^01[016789]-?\\d{3,4}-?\\d{4}$", message = "전화번호 형식이 올바르지 않습니다.")
        String phone,

        @Schema(description = "주소", example = "서울 강남구")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @Schema(description = "AI 매칭 사용 여부. 끄면 추천 대상에서 제외된다.", example = "true")
        @NotNull(message = "AI 매칭 동의 여부는 필수입니다.")
        Boolean aiMatchingAgreed
) {

    public FreelancerProfileUpdateCommand toCommand(Long accountId) {
        return new FreelancerProfileUpdateCommand(accountId, profileFileId, phone, address, aiMatchingAgreed);
    }
}
