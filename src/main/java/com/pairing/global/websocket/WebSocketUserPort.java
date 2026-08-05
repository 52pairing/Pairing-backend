package com.pairing.global.websocket;

import java.util.Optional;

/**
 * JWT subject(이메일/로그인 ID)로 사용자 PK를 찾는 포트.
 *
 * <p>global은 도메인을 참조하지 않으므로 조회 방법을 여기서 정의만 하고, 구현은 사용자 도메인이 담당한다.
 *
 * <p><b>구현체는 선택 사항이다.</b> 빈이 없으면 핸드셰이크는 subject만 세션에 담고 통과시키며,
 * {@code userId} 세션 속성은 null이 된다. 사용자 PK가 필요한 기능
 * (예: {@link PresenceEventListener} 의 온라인 상태 기록)은 구현체를 등록해야 동작한다.
 *
 * <p>구현 예시
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class UserWebSocketUserPortAdapter implements WebSocketUserPort {
 *
 *     private final UserRepository userRepository;
 *
 *     @Override
 *     public Optional<Long> findUserIdByPrincipal(String principal) {
 *         return userRepository.findByEmail(principal).map(User::getId);
 *     }
 * }
 * }</pre>
 */
public interface WebSocketUserPort {

    /**
     * @param principal JWT subject. 이메일 또는 로그인 ID다.
     * @return 사용자 PK. 없으면 {@link Optional#empty()} 이며 핸드셰이크가 거절된다.
     */
    Optional<Long> findUserIdByPrincipal(String principal);
}
