package com.pairing.negotiation.application.port.out;

/**
 * 협상 타결 시 사람 채팅방을 여는 아웃바운드 포트(협상 소유). 채팅 도메인이 구현한다.
 *
 * <p>협상은 "타결됐으니 이 협상의 채팅방을 열어달라"는 요청만 하고, 방·참여자 구성은 채팅 도메인이
 * 스스로 조회해 처리한다(협상은 채팅 내부를 모른다). 협상 응답 트랜잭션 안에서 동기 호출되므로
 * 방 생성이 실패하면 타결도 함께 롤백된다(원자성).
 */
public interface ChatRoomCreationPort {

    /**
     * 타결된 협상의 채팅방을 연다. 이미 방이 있으면(재호출) 아무 것도 하지 않는다(멱등).
     *
     * @param negotiationId 타결된 협상 ID
     */
    void createForAgreedNegotiation(Long negotiationId);
}
