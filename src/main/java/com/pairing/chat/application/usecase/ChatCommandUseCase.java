package com.pairing.chat.application.usecase;

import com.pairing.chat.application.result.ChatMessageView;

/** 채팅 명령 인바운드 포트. */
public interface ChatCommandUseCase {

    /**
     * 타결된 협상의 채팅방을 연다(협상 도메인이 {@code ChatRoomCreationPort} 로 호출). 이미 있으면 멱등.
     */
    void provisionForAgreedNegotiation(Long negotiationId);

    /** 메시지 전송(참여자 + 입력창 활성 필요). 저장 후 실시간 broadcast 한다. */
    ChatMessageView sendMessage(Long chatRoomId, Long accountId, String content);

    /** 읽음 처리(마지막 읽은 시각 = 현재). */
    void markAsRead(Long chatRoomId, Long accountId);

    /** 방 나가기(방 종료 후에만 허용). */
    void leave(Long chatRoomId, Long accountId);
}
