package com.pairing.negotiation.domain.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 대리인(A2A) 실행 상태.
 *
 * <p>A2A 호출이 <b>요청 스레드 밖</b>에서 돌기 때문에 필요하다. 동기였을 땐 결과가 HTTP 응답에
 * 실려 나가서 "도는 중"이라는 상태 자체가 없었다 — 응답이 오면 이미 끝난 것이었다.
 *
 * <p>이 값은 세 가지를 한꺼번에 해결한다.
 * <ol>
 *   <li><b>화면 문구</b> — {@code waitingForMe=false} 는 "상대 응답 대기"와 "대리인이 도는 중"을
 *       구분하지 못한다. 동기일 땐 후자가 17초뿐이라 넘어갔지만 비동기에선 관측 가능한 상태가 된다</li>
 *   <li><b>조용한 실패 노출</b> — 비동기라 예외가 호출한 쪽으로 가지 않는다. {@link #FAILED} 가
 *       없으면 화면은 영원히 "협상 중"에 멈춘 채 아무도 원인을 모른다</li>
 *   <li><b>중복 실행 방지</b> — {@code IDLE → RUNNING} 조건부 전이라, 이미 도는 중이면 두 번째
 *       요청이 대리인을 또 돌리지 못한다. 파생값으로는 이걸 원자적으로 할 수 없다</li>
 * </ol>
 *
 * <p><b>기존 행은 이 컬럼이 null 이다.</b> 배포된 RDS 에 컬럼을 새로 붙이는 것이라
 * ({@code ddl-auto: update}) 이미 있던 협상 행에는 값이 안 들어간다.
 * {@link Negotiation#reconstitute} 가 null 을 {@link #IDLE} 로 읽는다 — 옛 협상은 대리인이
 * 도는 중일 수 없으니 그게 맞다.
 */
@Getter
@RequiredArgsConstructor
public enum NegotiationAgentState {

    /** 도는 중이 아니다. 사람 차례이거나 상대를 기다리는 중. */
    IDLE("대기"),

    /** 대리인이 협상 중. 이 상태에선 사람 응답을 받지 않는다. */
    RUNNING("대리인 협상 중"),

    /** 대리인 호출이 실패해 라운드가 진행되지 못했다. 재시도가 필요하다. */
    FAILED("대리인 호출 실패");

    private final String label;
}
