package com.pairing.contract.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계약 상태. (요구사항 R43)
 *
 * <p>협상이 타결되면 계약서가 DRAFT 로 만들어진다. 본문의 자유 텍스트를 AI 가 다듬는 동안은
 * 아직 읽을 계약서가 아니므로 <b>서명할 수 없다</b>. 문구가 채워지면 SIGN_PENDING 으로 넘어간다.
 *
 * <p>둘 다 서명해야 SIGNED 가 되며 이 시점에 착수금 수수료가 발생한다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractStatus {

    DRAFT("작성 중"),
    SIGN_PENDING("서명 대기"),
    SIGNED("체결 완료"),
    COMPLETED("이행 완료"),
    REJECTED("서명 거부"),
    TERMINATED("중도 파기");

    private final String label;
}
