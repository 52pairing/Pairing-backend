package com.pairing.global.websocket;

/**
 * WebSocket 세션 속성 키.
 *
 * <p>핸드셰이크에서 넣은 값을 STOMP 이벤트 리스너와 {@code @MessageMapping} 핸들러가 다시 꺼내 쓴다.
 * 문자열 리터럴이 흩어지면 오타로 값이 항상 null이 되어도 컴파일 에러가 나지 않으므로 여기에 모아둔다.
 */
public final class WebSocketSessionAttributes {

    /** 사용자 PK. {@link WebSocketUserPort} 가 등록되어 있을 때만 채워진다. (Long) */
    public static final String USER_ID = "userId";

    /** JWT subject. 이메일 또는 로그인 ID다. 항상 채워진다. (String) */
    public static final String PRINCIPAL = "principal";

    /** JWT role 클레임. 토큰에 없으면 null이다. (String) */
    public static final String ROLE = "role";

    private WebSocketSessionAttributes() {
        throw new IllegalStateException("Utility class");
    }
}
