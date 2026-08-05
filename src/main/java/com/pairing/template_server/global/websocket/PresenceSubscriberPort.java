package com.pairing.template_server.global.websocket;

import java.util.List;

/**
 * 어떤 사용자의 접속 상태 변경을 <b>누구에게</b> 알릴지 결정하는 포트.
 *
 * <p>원본 구현은 친구 목록을 직접 조회했다. global이 특정 도메인(friend)을 참조하지 않도록
 * "구독자 목록"만 받아오는 포트로 분리했다. 프로젝트에 따라 친구, 채팅방 참여자,
 * 협상 상대 등 무엇이든 구독자가 될 수 있다.
 *
 * <p><b>구현체는 선택 사항이다.</b> 빈이 없으면 Redis 온라인 플래그와 메트릭만 기록하고
 * 상태 변경 push는 하지 않는다.
 *
 * <p>구현 예시
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class ChatRoomPresenceSubscriberAdapter implements PresenceSubscriberPort {
 *
 *     private final ChatRoomQueryUseCase chatRoomQueryUseCase;
 *
 *     @Override
 *     public List<Long> findSubscriberUserIds(Long userId) {
 *         return chatRoomQueryUseCase.getCounterpartUserIds(userId);
 *     }
 * }
 * }</pre>
 */
public interface PresenceSubscriberPort {

    /**
     * @param userId 접속 상태가 바뀐 사용자 PK
     * @return 알림을 받을 사용자 PK 목록. 없으면 빈 리스트를 반환한다. (null 반환 금지)
     */
    List<Long> findSubscriberUserIds(Long userId);
}
