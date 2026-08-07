package com.pairing.account.presentation.api.response;

import com.pairing.account.domain.model.AccountStatus;
import com.pairing.account.domain.model.Role;
import com.pairing.account.domain.model.SignupType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** [관리자] 회원 목록 항목. (요구사항 R37) */
@Schema(description = "관리자 회원 응답")
public record AdminAccountResponse(

        @Schema(description = "계정 ID", example = "7") Long accountId,
        @Schema(description = "화면에 표시하는 회원번호", example = "MEM-001") String memberNo,
        @Schema(description = "유형") Role role,
        @Schema(description = "이름 또는 기업명", example = "주식회사 페어링") String name,
        @Schema(description = "담당자명. 클라이언트만 채워진다", example = "김담당") String managerName,
        @Schema(description = "이메일", example = "owner@pairing.com") String email,
        @Schema(description = "가입 방식") SignupType signupType,
        @Schema(description = "상태") AccountStatus status,
        @Schema(description = "정지 여부. 정지는 상태와 별도로 관리한다.", example = "false") boolean suspended,
        @Schema(description = "정지 사유") String suspendReason,
        @Schema(description = "진행 중 프로젝트 수", example = "2") int activeProjectCount,
        @Schema(description = "가입일") LocalDateTime createdAt,
        @Schema(description = "최근 로그인") LocalDateTime lastLoginAt
) {
}
