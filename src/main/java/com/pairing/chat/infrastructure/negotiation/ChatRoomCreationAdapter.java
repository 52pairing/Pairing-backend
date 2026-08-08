package com.pairing.chat.infrastructure.negotiation;

import com.pairing.chat.application.usecase.ChatCommandUseCase;
import com.pairing.negotiation.application.port.out.ChatRoomCreationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 협상 도메인의 {@link ChatRoomCreationPort} 를 채팅 도메인이 구현한다. 협상 타결 시 호출되어
 * 채팅방을 연다. 협상 응답 트랜잭션에 참여하므로 방 생성 실패 시 타결도 롤백된다.
 */
@Component
@RequiredArgsConstructor
public class ChatRoomCreationAdapter implements ChatRoomCreationPort {

    private final ChatCommandUseCase chatCommandUseCase;

    @Override
    public void createForAgreedNegotiation(Long negotiationId) {
        chatCommandUseCase.provisionForAgreedNegotiation(negotiationId);
    }
}
