package com.pairing.chat.application.service;

import com.pairing.chat.application.port.out.ChatDirectoryPort;
import com.pairing.chat.application.usecase.ChatActivationUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** 조회 시점 채팅방 복구: 대상 선별과 "체결 전에는 열지 않는다" 규칙. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatRoomRepairerTest {

    private static final Long ACCOUNT_ID = 700L;

    @Mock
    private ChatDirectoryPort chatDirectoryPort;
    @Mock
    private ChatActivationUseCase chatActivationUseCase;

    @InjectMocks
    private ChatRoomRepairer repairer;

    @Test
    @DisplayName("체결됐는데 방이 없는 협상은 전부 연다")
    void opensEveryMissingRoom() {
        given(chatDirectoryPort.findNegotiationIdsMissingRoom(ACCOUNT_ID)).willReturn(List.of(28L, 30L));

        repairer.repairMyRooms(ACCOUNT_ID);

        verify(chatActivationUseCase).openForSignedContract(28L);
        verify(chatActivationUseCase).openForSignedContract(30L);
    }

    @Test
    @DisplayName("정상 상태(빠진 방 없음)에서는 아무 것도 하지 않는다")
    void doesNothingWhenNoRoomMissing() {
        given(chatDirectoryPort.findNegotiationIdsMissingRoom(ACCOUNT_ID)).willReturn(List.of());

        repairer.repairMyRooms(ACCOUNT_ID);

        verify(chatActivationUseCase, never()).openForSignedContract(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("계약이 체결되지 않았으면 열지 않는다 — 무산됐을 때 빈 방이 남는다")
    void neverOpensBeforeContractConcluded() {
        given(chatDirectoryPort.isContractConcluded(28L)).willReturn(false);

        repairer.repairIfConcluded(28L);

        verify(chatActivationUseCase, never()).openForSignedContract(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("체결된 계약이면 그 자리에서 연다")
    void opensWhenContractConcluded() {
        given(chatDirectoryPort.isContractConcluded(28L)).willReturn(true);

        repairer.repairIfConcluded(28L);

        verify(chatActivationUseCase).openForSignedContract(28L);
    }
}
