package com.pairing.support.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 1:1 문의 유형.
 *
 * <p>작성 화면에는 선택 UI 가 없고 상세 화면에만 "문의 유형: 결제·수수료" 로 표시된다.
 * 사용자가 고르지 않으면 서버가 내용으로 분류한다.
 */
@Getter
@RequiredArgsConstructor
public enum InquiryCategory {

    ACCOUNT("계정·로그인"),
    PROJECT("프로젝트 등록"),
    MATCHING("매칭·추천"),
    NEGOTIATION("협상"),
    CONTRACT("계약"),
    PAYMENT("결제·수수료"),
    REVIEW("리뷰·평점"),
    ETC("기타");

    private final String label;
}
