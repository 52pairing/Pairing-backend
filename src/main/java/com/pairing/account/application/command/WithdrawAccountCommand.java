package com.pairing.account.application.command;

/**
 * 회원 탈퇴. (요구사항 R17, R31)
 *
 * <p>{@code confirmText} 는 화면에서 받은 확인 문구다. 서버가 정해진 문구와 맞는지 다시 본다 —
 * 프론트만 검사하면 API 를 직접 부르는 경로가 뚫린다.
 */
public record WithdrawAccountCommand(
        Long accountId,
        String confirmText,
        String reason
) {
}
