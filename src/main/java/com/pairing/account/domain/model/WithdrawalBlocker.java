package com.pairing.account.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 탈퇴를 막는 사유. (요구사항 R31)
 *
 * <p>화면이 "진행 중인 협상이 1건 → 협상 확인하기" 처럼 사유별로 안내하고 이동 링크를 걸어야 해서,
 * 문구와 경로를 서버가 함께 내려준다. 프론트가 타입으로 문구를 조립하면 서버가 사유를 늘릴 때마다
 * 화면도 같이 고쳐야 한다.
 *
 * <p>사용자가 남긴 기록(완료된 프로젝트·리뷰·채팅)은 탈퇴를 막지 않는다. 여기 있는 건
 * <b>아직 끝나지 않아 상대방이 기다리고 있는 일</b>뿐이다.
 */
@Getter
@RequiredArgsConstructor
public enum WithdrawalBlocker {

    NEGOTIATION("진행 중인 협상", "/negotiations"),
    PROJECT("진행 중인 프로젝트", "/my-projects"),
    SIGN_PENDING_CONTRACT("서명 대기 계약", "/contracts"),
    CONTRACT("진행 중인 계약", "/contracts"),
    UNPAID_SETTLEMENT("미납 수수료", "/mypage/settlements");

    private final String label;
    private final String linkUrl;

    /** 정산만 성격이 다르다. 결제하면 풀리고, 나머지는 거래가 끝나야 풀린다. */
    public boolean isSettlement() {
        return this == UNPAID_SETTLEMENT;
    }
}
