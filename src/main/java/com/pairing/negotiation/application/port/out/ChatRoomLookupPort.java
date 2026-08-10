package com.pairing.negotiation.application.port.out;

import java.util.Optional;

/**
 * 협상에 연결된 채팅방 ID 조회 아웃포트(협상 소유, 읽기). 채팅 도메인이 구현한다.
 *
 * <p>타결(AI Out) 후 협상 상세의 "채팅으로 이어가기" 이동에 쓸 chatRoomId 를 채우기 위한 것.
 * 채팅방이 아직 없으면 empty. 방은 계약 체결 시 열리므로, 협상 중·타결 직후·결렬은 모두 empty 다.
 */
public interface ChatRoomLookupPort {

    Optional<Long> findChatRoomIdByNegotiationId(Long negotiationId);
}
