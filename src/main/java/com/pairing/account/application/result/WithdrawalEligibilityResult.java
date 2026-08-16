package com.pairing.account.application.result;

import com.pairing.account.domain.model.WithdrawalBlocker;

import java.util.List;

/**
 * 탈퇴 가능 여부. 마이페이지 &gt; 회원 탈퇴 화면이 진입할 때 쓴다.
 *
 * <p>{@code blockers} 가 비어 있으면 탈퇴할 수 있다. 화면은 이 목록으로 안내 문구와
 * 이동 링크를 그리고, 비어 있을 때만 탈퇴 버튼을 연다.
 */
public record WithdrawalEligibilityResult(
        boolean withdrawable,
        List<Blocked> blockers
) {

    /** {@code linkUrl} 은 역할까지 반영된 최종 경로다. 역할을 아는 건 서비스뿐이라 여기서 들고 온다. */
    public record Blocked(WithdrawalBlocker blocker, long count, String linkUrl) {
    }

    public static WithdrawalEligibilityResult of(List<Blocked> blockers) {
        return new WithdrawalEligibilityResult(blockers.isEmpty(), blockers);
    }
}
