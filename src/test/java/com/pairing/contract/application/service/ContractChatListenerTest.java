package com.pairing.contract.application.service;

import com.pairing.chat.application.usecase.ChatActivationUseCase;
import com.pairing.contract.application.event.ContractSignedEvent;
import com.pairing.contract.domain.model.Contract;
import com.pairing.contract.domain.repository.ContractRepository;
import com.pairing.meta.domain.model.WorkForm;
import com.pairing.meta.domain.model.WorkStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 체결 뒤 1:1 채팅방을 여는 규칙.
 *
 * <p>핵심은 <b>실패가 밖으로 안 나간다</b>는 것이다. 이 리스너는 커밋 뒤에 돌기 때문에 여기서
 * 예외가 새면 이미 체결된 계약을 되돌릴 수도 없으면서 요청만 실패시킨다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractChatListenerTest {

    private static final Long CONTRACT_ID = 600L;
    private static final Long NEGOTIATION_ID = 300L;

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ChatActivationUseCase chatActivationUseCase;

    private ContractChatListener listener;

    @BeforeEach
    void setUp() {
        listener = new ContractChatListener(contractRepository, chatActivationUseCase);
        given(contractRepository.findById(CONTRACT_ID)).willReturn(Optional.of(contract()));
    }

    @Test
    @DisplayName("체결된 계약의 협상 ID 로 채팅방을 연다")
    void opensRoomForNegotiation() {
        listener.on(event());

        verify(chatActivationUseCase).openForSignedContract(NEGOTIATION_ID);
    }

    @Test
    @DisplayName("채팅방 개설이 실패해도 예외가 밖으로 나가지 않는다")
    void swallowsChatFailure() {
        // 커밋이 끝난 뒤라 계약을 되돌릴 수 없다. 여기서 예외가 새면 체결은 됐는데 요청만 실패한다.
        willThrow(new IllegalStateException("협상 당사자 정보를 찾을 수 없습니다"))
                .given(chatActivationUseCase).openForSignedContract(any());

        assertThatCode(() -> listener.on(event())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("계약을 못 찾으면 채팅방을 열지 않고 조용히 끝낸다")
    void skipsWhenContractGone() {
        given(contractRepository.findById(CONTRACT_ID)).willReturn(Optional.empty());

        assertThatCode(() -> listener.on(event())).doesNotThrowAnyException();
        verify(chatActivationUseCase, never()).openForSignedContract(any());
    }

    private ContractSignedEvent event() {
        return new ContractSignedEvent(CONTRACT_ID, 1L, 10L, 200L);
    }

    private Contract contract() {
        return Contract.create(NEGOTIATION_ID, 1L, 10L, 100L, 200L,
                900_001L, 900_002L, 5_000_000L, 4,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31),
                WorkStyle.REMOTE, WorkForm.FULL_TIME, null, null);
    }
}
