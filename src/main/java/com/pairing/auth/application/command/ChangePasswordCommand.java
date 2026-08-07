package com.pairing.auth.application.command;

/**
 * 마이페이지 비밀번호 변경.
 *
 * <p>현재 비밀번호 대신 이메일 인증코드로 본인을 확인한다. 코드 검증은 이 커맨드가 오기 전에
 * {@code POST /auth/email-verifications/confirm} 에서 끝나 있고, 여기서는 인증 마커만 확인한다.
 */
public record ChangePasswordCommand(
        Long accountId,
        String newPassword,
        String newPasswordConfirm
) {
}
