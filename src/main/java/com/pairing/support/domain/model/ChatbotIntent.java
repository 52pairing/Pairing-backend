package com.pairing.support.domain.model;

import com.pairing.account.domain.model.Role;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 챗봇 답변과 이어지는 화면. 답변 아래 버튼으로 그려진다.
 *
 * <p>AI 에게 URL 을 만들게 하지 않는다. 없는 경로를 지어내거나 형식이 매번 달라져서,
 * AI 는 이 목록 중 코드 하나만 고르고 실제 경로는 여기서 정한다. 경로가 바뀌어도
 * 프롬프트를 건드릴 필요가 없다.
 *
 * <p>목록을 늘릴수록 AI 가 잘못 고른다. 챗봇 추천 질문이 실제로 가리키는 화면까지만 둔다.
 */
@Getter
@RequiredArgsConstructor
public enum ChatbotIntent {

    RESUME_EDIT("이력서 작성하러 가기", "/mypage/resume", Role.FREELANCER),
    PROJECT_CREATE("프로젝트 등록하기", "/projects/new", Role.CLIENT),

    PAYMENT_METHOD("결제수단 관리", "/mypage/payment-methods", null),
    SETTLEMENTS("수수료 결제 내역", "/mypage/settlements", null),
    MY_PROJECTS("내 프로젝트 보기", "/my-projects", null),
    NEGOTIATION_LIST("협상 목록 보기", "/negotiations", null),
    CONTRACTS("계약 관리 보기", "/contracts", null),
    INQUIRY_NEW("1:1 문의하기", "/support/inquiries/new", null),

    /** 이어질 화면이 없다. 버튼을 그리지 않는다. */
    NONE(null, null, null);

    private final String label;
    private final String url;

    /** 이 화면을 쓸 수 있는 역할. null 이면 누구나. */
    private final Role requiredRole;

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

    /**
     * 역할에 맞지 않으면 버튼을 없앤다.
     *
     * <p>프리랜서가 프로젝트 등록을 물으면 AI 는 {@code PROJECT_CREATE} 를 고르지만, 프리랜서는
     * 프로젝트를 등록할 수 없다. 눌러도 막히는 버튼을 띄우면 안내가 아니라 오답이 된다.
     * 답변 자체는 그대로 나간다.
     */
    public ChatbotIntent filterFor(Role role) {
        if (requiredRole == null || requiredRole == role) {
            return this;
        }
        return NONE;
    }

    public boolean hasAction() {
        return this != NONE;
    }
}
