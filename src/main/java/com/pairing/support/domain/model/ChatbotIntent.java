package com.pairing.support.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 챗봇 답변과 이어지는 화면. 답변 아래 버튼으로 그려진다.
 *
 * <p>AI 에게 URL 을 만들게 하지 않는다. 없는 경로를 지어내거나 형식이 매번 달라져서,
 * AI 는 이 목록 중 코드 하나만 고르고 실제 경로는 여기서 정한다. 경로가 바뀌어도
 * 프롬프트를 건드릴 필요가 없다.
 *
 * <p>목록을 늘릴수록 AI 가 잘못 고른다. 정말 자주 쓰는 화면만 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum ChatbotIntent {

    RESUME_EDIT("이력서 작성하러 가기", "/mypage/resume"),
    PAYMENT_METHOD("결제수단 관리", "/mypage/payment-methods"),
    MY_PROJECTS("내 프로젝트 보기", "/my-projects"),
    INQUIRY_NEW("1:1 문의하기", "/support/inquiries/new"),

    /** 이어질 화면이 없다. 버튼을 그리지 않는다. */
    NONE(null, null);

    private final String label;
    private final String url;

    /**
     * 모르는 코드는 {@link #NONE} 으로 떨어뜨린다.
     *
     * <p>AI 응답이라 목록에 없는 값이 올 수 있다. 예외로 터뜨리면 답변까지 못 보여주게 되므로,
     * 최악이라도 버튼만 빠지고 답변은 나가도록 한다.
     */
    public static ChatbotIntent from(String code) {
        if (code == null || code.isBlank()) {
            return NONE;
        }
        for (ChatbotIntent intent : values()) {
            if (intent.name().equals(code)) {
                return intent;
            }
        }
        return NONE;
    }

    public boolean hasAction() {
        return this != NONE;
    }
}
