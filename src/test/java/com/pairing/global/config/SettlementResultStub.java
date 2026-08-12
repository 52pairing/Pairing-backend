package com.pairing.global.config;

import com.pairing.meta.domain.model.PartyRole;
import com.pairing.settlement.application.result.SettlementResult;
import com.pairing.settlement.domain.model.SettlementPhase;
import com.pairing.settlement.domain.model.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 리뷰 작성 조건 중 "본인 성공보수 납부"를 만족시키는 정산 1건.
 *
 * <p>리뷰·등급·홈 테스트는 정산 도메인을 검증하는 게 목적이 아니라, 정산 조회 결과만 대신한다.
 * 리뷰 서비스는 결과가 비었는지만 보므로 금액·번호는 아무 값이나 채운다.
 */
public final class SettlementResultStub {

    private SettlementResultStub() {
    }

    public static SettlementResult paidSuccessFee() {
        return new SettlementResult(9001L, "ST-2026-009001", 1L, 1L,
                PartyRole.CLIENT, SettlementPhase.SUCCESS_FEE, 10_000_000L,
                new BigDecimal("6.00"), BigDecimal.ZERO, 600_000L, SettlementStatus.PAID,
                "AP-TEST", null, null, null, false, LocalDateTime.now());
    }
}
