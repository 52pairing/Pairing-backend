package com.pairing.contract.application.service;

import com.pairing.contract.domain.repository.ContractRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * DRAFT 로 멈춘 계약을 다시 채우는 배치 규칙.
 *
 * <p>여기서 검증하려는 것은 처리량이 아니라 <b>물러나는 조건</b>이다. 파이썬이 죽어 있을 때
 * 배치가 계속 찌르면 매칭·협상·챗봇이 함께 쓰는 Gemini 키의 쿨다운이 밀린다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContractDraftRecoveryServiceTest {

    @Mock
    private ContractRepository contractRepository;
    @Mock
    private ContractDraftFiller draftFiller;

    private ContractDraftRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new ContractDraftRecoveryService(contractRepository, draftFiller);
        ReflectionTestUtils.setField(recoveryService, "staleAfterMinutes", 5L);
        ReflectionTestUtils.setField(recoveryService, "batchSize", 20);
        ReflectionTestUtils.setField(recoveryService, "probeTimeoutSeconds", 10L);
    }

    private void stuck(Long... ids) {
        given(contractRepository.findStuckDraftIds(any(LocalDateTime.class), anyInt()))
                .willReturn(List.of(ids));
    }

    @Test
    @DisplayName("멈춘 계약이 없으면 아무것도 부르지 않는다")
    void doesNothingWhenNoStuckDrafts() {
        stuck();

        recoveryService.recoverStuckDrafts();

        verifyNoInteractions(draftFiller);
    }

    @Test
    @DisplayName("선두가 실패하면 나머지는 다음 주기로 미룬다")
    void skipsRestWhenProbeFails() {
        // 파이썬이 죽었다는 뜻이라 나머지를 찔러도 결과는 같고, 공유 Gemini 키만 더 소모한다.
        stuck(1L, 2L, 3L);
        given(draftFiller.probe(1L)).willReturn(CompletableFuture.completedFuture(false));

        recoveryService.recoverStuckDrafts();

        verify(draftFiller).probe(1L);
        verify(draftFiller, never()).fill(anyLong());
    }

    @Test
    @DisplayName("선두가 성공하면 나머지를 이어서 채운다")
    void fillsRestWhenProbeSucceeds() {
        stuck(1L, 2L, 3L);
        given(draftFiller.probe(1L)).willReturn(CompletableFuture.completedFuture(true));

        recoveryService.recoverStuckDrafts();

        verify(draftFiller).probe(1L);
        verify(draftFiller).fill(2L);
        verify(draftFiller).fill(3L);
        // 선두는 탐침에서 이미 처리됐다. 두 번 채우지 않는다.
        verify(draftFiller, never()).fill(1L);
    }

    @Test
    @DisplayName("탐침이 시간 안에 끝나지 않으면 나머지를 보내지 않는다")
    void skipsRestWhenProbeTimesOut() {
        // 파이썬이 느린 것이니 남은 건을 보내도 같은 대기를 반복할 뿐이다.
        stuck(1L, 2L);
        ReflectionTestUtils.setField(recoveryService, "probeTimeoutSeconds", 0L);
        given(draftFiller.probe(1L)).willReturn(new CompletableFuture<>());   // 끝나지 않는다

        recoveryService.recoverStuckDrafts();

        verify(draftFiller, never()).fill(anyLong());
    }
}
