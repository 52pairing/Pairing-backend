package com.pairing.settlement.application.service;

import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.settlement.domain.model.Settlement;
import com.pairing.settlement.domain.model.SettlementStatus;
import com.pairing.settlement.domain.repository.SettlementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 프로젝트가 취소되면 미결제 수수료를 접는다. (정책 P46)
 *
 * <p>남겨두면 프리랜서 화면에 "결제 필요" 배지가 계속 뜬다. 죽은 프로젝트의 수수료를 내라고
 * 재촉하는 셈이고, 실제로 결제까지 이어지면 되돌릴 방법이 없다.
 *
 * <p>기존 {@code cancelPayable} 은 <b>클라이언트 착수금 1건만</b> 본다. 등록 취소용이라 그때는
 * 그것뿐이라 맞았는데, 모집 만료 시점에는 계약이 체결된 만큼 프리랜서 착수금이 여러 건 붙는다.
 * 그 차이를 잠근다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettlementProjectCancelListenerTest {

    private static final Long PROJECT_ID = 9_900L;

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private DepositSettlementService depositSettlementService;

    /*
     * 이미 낸 수수료(PAID)를 건드리지 않는 것은 리포지토리 책임이다.
     * findAllPayableByProjectId 가 PAYABLE_STATUSES(PENDING·OVERDUE·FAILED)로 거른다.
     * 서비스는 받은 목록을 그대로 접으므로 여기서 다시 검증하지 않는다.
     */

    @Test
    @DisplayName("미결제 수수료를 전부 취소한다 — 1건만 처리하면 나머지가 남는다")
    void cancelsEveryPayable() {
        List<Settlement> payable = List.of(payable(), payable(), payable());
        given(settlementRepository.findAllPayableByProjectId(PROJECT_ID)).willReturn(payable);

        int canceled = depositSettlementService.cancelAllPayable(PROJECT_ID);

        assertThat(canceled).isEqualTo(3);
        assertThat(payable).allMatch(s -> s.getStatus() == SettlementStatus.CANCELED);
        verify(settlementRepository, times(3)).save(any());
    }

    @Test
    @DisplayName("낼 게 없으면 저장도 하지 않는다")
    void doesNothingWhenNoPayable() {
        given(settlementRepository.findAllPayableByProjectId(PROJECT_ID)).willReturn(List.of());

        assertThat(depositSettlementService.cancelAllPayable(PROJECT_ID)).isZero();
        verify(settlementRepository, never()).save(any());
    }

    @Test
    @DisplayName("리스너가 이벤트를 받아 유스케이스로 넘긴다")
    void listenerDelegates() {
        // 도메인 로직이 멀쩡해도 리스너가 이벤트에 안 걸려 있으면 아무 일도 안 일어난다.
        var useCase = org.mockito.Mockito.mock(
                com.pairing.settlement.application.usecase.DepositSettlementUseCase.class);
        var listener = new SettlementProjectCancelListener(useCase);

        listener.on(new ProjectCanceledEvent(PROJECT_ID));

        verify(useCase).cancelAllPayable(PROJECT_ID);
    }

    private Settlement payable() {
        return Settlement.createFreelancerDeposit(PROJECT_ID, 1L, 2000L,
                25_000_000L, new BigDecimal("4.00"), BigDecimal.ZERO, 1_000_000L);
    }
}
