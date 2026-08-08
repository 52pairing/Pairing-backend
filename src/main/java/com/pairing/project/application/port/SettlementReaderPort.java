package com.pairing.project.application.port;

import java.util.Optional;

/**
 * 프로젝트 화면의 결제 버튼이 쓰는 정산 조회.
 *
 * <p>정산 도메인 구현을 감싼다. 프로젝트는 정산 금액이나 상태를 알 필요가 없고
 * "지금 결제할 게 있는가"만 알면 된다.
 */
public interface SettlementReaderPort {

    /** 결제 대기 중인 정산 ID. 없으면 empty. */
    Optional<Long> findPayableSettlementId(Long projectId);
}
