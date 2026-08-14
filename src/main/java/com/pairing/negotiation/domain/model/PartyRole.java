package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/** 협상 당사자 구분. 마지노선·승인 등에서 클라이언트/프리랜서를 나눈다. */
@Getter
@RequiredArgsConstructor
public enum PartyRole {

    CLIENT("클라이언트"),
    FREELANCER("프리랜서");

    private final String label;

    /** 상대 당사자. 최종 절충안에서 "상대가 수락했는가"를 뷰어 기준으로 고를 때 쓴다. */
    public PartyRole opposite() {
        return this == CLIENT ? FREELANCER : CLIENT;
    }

    /**
     * 이 당사자 쪽 발신자(본인 + 본인 대리인).
     *
     * <p>"상대가 낸 제안"을 고를 때 <b>빼야 할</b> 발신자 목록이다. 화면에 띄울 제안값도,
     * 수락 시 락할 값도 이 집합을 제외하고 찾는다.
     */
    public List<SenderType> ownSenders() {
        return this == CLIENT
                ? List.of(SenderType.CLIENT, SenderType.CLIENT_AGENT)
                : List.of(SenderType.FREELANCER, SenderType.FREELANCER_AGENT);
    }
}
