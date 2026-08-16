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

    NEGOTIATION("진행 중인 협상", "/client/projects"),
    PROJECT("진행 중인 프로젝트", "/client/projects"),
    SIGN_PENDING_CONTRACT("서명 대기 계약", "/freelancer/contracts"),
    /** SIGNED(체결 완료)도 여기 걸린다. "진행 중"이라고 하면 체결 화면 문구와 어긋나 보인다. */
    CONTRACT("아직 끝나지 않은 계약", "/freelancer/contracts"),
    UNPAID_SETTLEMENT("미납 수수료", null);

    private final String label;

    /**
     * 역할이 고정인 사유의 경로. 협상·프로젝트는 클라이언트만, 계약은 프리랜서만 걸리므로
     * 상수로 둘 수 있다. 미납 수수료만 양쪽에서 걸려 {@code null} 이고 {@link #linkUrl(Role)} 이 채운다.
     */
    private final String fixedLinkUrl;

    /**
     * 화면 경로. 프론트 라우트가 {@code /client/…} · {@code /freelancer/…} 로 갈려서 접두사가 필요하다.
     * 접두사 없는 경로를 내려주면 그 링크는 전부 404 가 된다.
     */
    public String linkUrl(Role role) {
        if (this == UNPAID_SETTLEMENT) {
            return role == Role.CLIENT ? "/client/mypage/payments" : "/freelancer/mypage/payments";
        }
        return fixedLinkUrl;
    }

    /** 정산만 성격이 다르다. 결제하면 풀리고, 나머지는 거래가 끝나야 풀린다. */
    public boolean isSettlement() {
        return this == UNPAID_SETTLEMENT;
    }
}
