package com.pairing.negotiation.domain.model;

/**
 * 한 당사자의 마지노선을 <b>어느 방향으로</b> 비교하는가. 조건타입×역할마다 다르다.
 *
 * <p><b>이 값이 방향의 단일 진실 원본</b>이다. 예전엔 "프리=하한, 클라=상한"을 가드
 * ({@link com.pairing.negotiation.domain.service.NegotiationFloorGuard})·프롬프트(파이썬)·프론트가
 * 각자 하드코딩했다. 그 결과 START_DATE 처럼 방향이 다른 쟁점(양측 모두 '늦어도 이 날까지' = 상한)에서
 * 세 곳이 어긋났다. 이제 {@link ConditionType#floorDirectionFor}가 정하고 모두 그걸 읽는다 —
 * 새 쟁점은 방향만 선언하면 세 곳을 다시 안 고쳐도 된다.
 */
public enum FloorDirection {

    /** 상한. 이 값을 <b>초과하면</b> 거절(합의값 ≤ 마지노선). 예: 클라 최대 단가, 시작일 최종 기한. */
    MAX,

    /** 하한. 이 값에 <b>못 미치면</b> 거절(합의값 ≥ 마지노선). 예: 프리 최소 단가·최소 기간. */
    MIN,

    /** 허용값 집합. 크기 비교가 없다({@code ANY}=전부 허용). 근무 방식·형태. */
    CHOICE,

    /** 비교 기준 없음. 자유 텍스트(업무 범위·기타)라 가드가 통과시킨다. */
    NONE
}
