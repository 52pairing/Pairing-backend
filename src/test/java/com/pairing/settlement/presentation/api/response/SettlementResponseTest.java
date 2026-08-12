package com.pairing.settlement.presentation.api.response;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 결제수단 표기가 응답까지 흘러오는지 본다.
 *
 * <p>이 자리는 한동안 {@code null} 로 고정돼 있었다. "결제수단 API 가 스켈레톤" 이라는 주석이
 * 남아 있었는데 실제로는 계정 도메인이 표기를 만들 수 있는 상태였다. 다시 굳지 않게 잠근다.
 */
class SettlementResponseTest {

    @Test
    @DisplayName("결제수단 표기가 응답에 그대로 담긴다")
    void carriesPaymentMethodLabel() {
        SettlementResponse response =
                SettlementResponse.from(paid(700L), "페어링 웹 리뉴얼", null, "신한카드 **** 1234");

        assertThat(response.paymentMethodLabel()).isEqualTo("신한카드 **** 1234");
        assertThat(response.approvalNo()).isEqualTo("AP-20260804-3821");
    }

    @Test
    @DisplayName("결제수단이 없거나 삭제됐으면 표기만 비고 결제 이력은 남는다")
    void keepsHistoryWhenMethodIsGone() {
        // 탈퇴·카드 삭제로 수단이 사라져도 승인번호와 결제 시각은 그대로여야 한다.
        SettlementResponse response = SettlementResponse.from(paid(null), "페어링 웹 리뉴얼", null, null);

        assertThat(response.paymentMethodLabel()).isNull();
        assertThat(response.approvalNo()).isEqualTo("AP-20260804-3821");
        assertThat(response.paidAt()).isNotNull();
    }

    private SettlementResult paid(Long paymentMethodId) {
        return new SettlementResult(
                700L, "ST-2026-000045", 1L, 600L,
                PartyRole.FREELANCER, SettlementPhase.DEPOSIT,
                24_800_000L, new BigDecimal("4.00"), BigDecimal.ZERO, 992_000L,
                SettlementStatus.PAID, paymentMethodId, "AP-20260804-3821",
                null, null, LocalDate.of(2026, 8, 11), false,
                LocalDateTime.of(2026, 8, 4, 14, 23, 11));
    }
}
