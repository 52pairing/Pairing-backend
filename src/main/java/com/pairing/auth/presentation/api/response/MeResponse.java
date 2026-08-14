package com.pairing.auth.presentation.api.response;

import com.pairing.account.domain.model.Account;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 로그인 사용자 응답")
public record MeResponse(

        @Schema(description = "계정 ID", example = "1")
        Long accountId,

        @Schema(description = "이메일", example = "user@pairing.com")
        String email,

        @Schema(description = "역할", example = "FREELANCER")
        String role,

        @Schema(description = "담당자명. 클라이언트는 대표자명이며 기업명이 아니다.", example = "홍길동")
        String name,

        @Schema(description = "기업명. 클라이언트만 채워지고 프리랜서는 null이다.", example = "주식회사 페어링")
        String companyName,

        @Schema(description = "임시 비밀번호 상태", example = "false")
        boolean tempPassword
) {

    /**
     * 화면에 찍는 이름은 역할마다 다르다. 클라이언트는 기업 회원이라 메인·프로필에 <b>기업명</b>이 나가야
     * 하는데 {@code name} 은 담당자명이라 그대로 쓸 수 없다.
     *
     * <p>두 값을 하나로 합치지 않고 따로 내리는 이유는 <b>프로필 화면이 기업명과 담당자명을 동시에</b>
     * 보여주기 때문이다. {@code name} 을 기업명으로 덮어쓰면 "담당자: OOO" 줄까지 기업명이 된다.
     *
     * @param companyName 클라이언트가 아니거나 프로필이 없으면 null
     */
    public static MeResponse from(Account account, String companyName) {
        return new MeResponse(
                account.getId(),
                account.getEmail(),
                account.getRole().name(),
                account.getName(),
                companyName,
                account.isTempPassword()
        );
    }
}
