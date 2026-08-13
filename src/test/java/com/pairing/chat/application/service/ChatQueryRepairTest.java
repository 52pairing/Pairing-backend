package com.pairing.chat.application.service;

import com.pairing.chat.application.port.out.ChatDirectoryPort;
import com.pairing.chat.domain.model.ChatMemberRole;
import com.pairing.chat.domain.model.ChatRoom;
import com.pairing.chat.domain.model.ChatRoomMember;
import com.pairing.chat.domain.repository.ChatMessageRepository;
import com.pairing.chat.domain.repository.ChatRoomRepository;
import com.pairing.chat.exception.ChatErrorCode;
import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/** 채팅 조회가 복구를 어떻게 부르는지: 순서·재조회·실패 격리. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatQueryRepairTest {

    private static final Long NEGOTIATION_ID = 28L;
    private static final Long CLIENT_ACCOUNT_ID = 700L;
    private static final Long FREELANCER_ACCOUNT_ID = 800L;

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatDirectoryPort chatDirectoryPort;
    @Mock
    private ChatRoomRepairer chatRoomRepairer;

    @InjectMocks
    private ChatQueryService queryService;

    private ChatRoom room() {
        return ChatRoom.open(NEGOTIATION_ID, List.of(
                ChatRoomMember.join(CLIENT_ACCOUNT_ID, ChatMemberRole.CLIENT),
                ChatRoomMember.join(FREELANCER_ACCOUNT_ID, ChatMemberRole.FREELANCER)));
    }

    @Test
    @DisplayName("협상으로 조회할 때 방이 없으면 복구를 부르고, 열린 방을 그대로 돌려준다")
    void repairsThenReturnsRoom() {
        given(chatRoomRepository.findByNegotiationId(NEGOTIATION_ID))
                .willReturn(Optional.empty())     // 첫 조회: 없다
                .willReturn(Optional.of(room())); // 복구 후 재조회: 열렸다

        assertThat(queryService.getRoomByNegotiation(NEGOTIATION_ID, CLIENT_ACCOUNT_ID)).isNotNull();
        verify(chatRoomRepairer).repairIfConcluded(NEGOTIATION_ID);
    }

    @Test
    @DisplayName("방이 이미 있으면 복구를 부르지 않는다")
    void skipsRepairWhenRoomExists() {
        given(chatRoomRepository.findByNegotiationId(NEGOTIATION_ID)).willReturn(Optional.of(room()));

        queryService.getRoomByNegotiation(NEGOTIATION_ID, CLIENT_ACCOUNT_ID);

        verify(chatRoomRepairer, org.mockito.Mockito.never()).repairIfConcluded(NEGOTIATION_ID);
    }

    @Test
    @DisplayName("복구해도 방이 없으면(체결 전) CH_001 그대로다")
    void stillNotFoundWhenNotConcluded() {
        given(chatRoomRepository.findByNegotiationId(NEGOTIATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getRoomByNegotiation(NEGOTIATION_ID, CLIENT_ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ChatErrorCode.CHAT_ROOM_NOT_FOUND);
    }

    @Test
    @DisplayName("목록 조회는 복구를 먼저 부른다 — 복구된 방이 이번 응답에 들어가야 한다")
    void repairsBeforeListing() {
        given(chatRoomRepository.findActiveRoomsByAccountId(CLIENT_ACCOUNT_ID)).willReturn(List.of());

        queryService.findMyRooms(CLIENT_ACCOUNT_ID);

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(chatRoomRepairer, chatRoomRepository);
        order.verify(chatRoomRepairer).repairMyRooms(CLIENT_ACCOUNT_ID);
        order.verify(chatRoomRepository).findActiveRoomsByAccountId(CLIENT_ACCOUNT_ID);
    }

    @Test
    @DisplayName("복구가 터져도 목록 조회는 성공한다")
    void listingSurvivesRepairFailure() {
        willThrow(new RuntimeException("복구 실패")).given(chatRoomRepairer).repairMyRooms(CLIENT_ACCOUNT_ID);
        given(chatRoomRepository.findActiveRoomsByAccountId(CLIENT_ACCOUNT_ID)).willReturn(List.of());

        assertThatCode(() -> queryService.findMyRooms(CLIENT_ACCOUNT_ID)).doesNotThrowAnyException();
    }
}
