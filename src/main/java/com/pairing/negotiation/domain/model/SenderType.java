package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 협상 메시지 작성 주체. AI 에이전트 제안과 사람 응답을 구분한다. */
@Getter
@RequiredArgsConstructor
public enum SenderType {

    CLIENT_AGENT("클라이언트 AI 에이전트"),
    FREELANCER_AGENT("프리랜서 AI 에이전트"),
    CLIENT("클라이언트"),
    FREELANCER("프리랜서"),
    SYSTEM("시스템");

    private final String label;
}
