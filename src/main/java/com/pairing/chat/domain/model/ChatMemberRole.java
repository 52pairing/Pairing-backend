package com.pairing.chat.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 채팅방 참여자의 역할. 협상 당사자를 그대로 옮겨온다(클라이언트/프리랜서 1:1).
 *
 * <p>협상 도메인의 {@code PartyRole} 과 값이 같지만, 채팅은 독립 도메인이므로 자체 enum 을 둔다
 * (협상 모델에 직접 의존하지 않는다).
 */
@Getter
@RequiredArgsConstructor
public enum ChatMemberRole {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서");

    private final String label;
}
