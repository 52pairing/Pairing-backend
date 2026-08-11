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
 *
 * <p>SIGNED 이후는 프로젝트를 따라간다. 계약관리 화면이 "진행 중 · 정산 대기 · 완료" 탭을
 * 계약 하나로 걸러야 하는데, 그 단계는 프로젝트 단위로 움직이기 때문이다. 전환은
 * {@code ContractLifecycleListener} 가 프로젝트 이벤트를 받아 처리한다.
 *
 * <p>매칭의 {@code matching_request.status} 와 값이 겹치지만 용도가 다르다. 그쪽은 "프로젝트 제안"
 * 화면의 제안 생애주기를, 이쪽은 계약관리 화면을 그린다. 둘 다 같은 이벤트를 듣는다.
 */
@Getter
@RequiredArgsConstructor
public enum ContractStatus {

    DRAFT("작성 중"),
    SIGN_PENDING("서명 대기"),
    SIGNED("체결 완료"),
    IN_PROGRESS("진행중"),
    COMPLETION_PENDING("정산 대기"),
    COMPLETED("종료"),
    REJECTED("서명 거부"),
    TERMINATED("중도 파기");

    /** 체결 이후 살아 있는 상태. 인원 확정 카운트와 파기 가능 판정이 함께 쓴다. */
    public boolean isConcluded() {
        return this == SIGNED || this == IN_PROGRESS
                || this == COMPLETION_PENDING || this == COMPLETED;
    }

    private final String label;
}
