package com.pairing.matching.application.port.out;

import com.pairing.matching.application.command.CreateNegotiationCommand;
import com.pairing.matching.application.result.NegotiationSummary;

import java.util.Optional;

/**
 * negotiation 도메인 호출 포트. 매칭은 이 인터페이스로만 협상 생성·조회를 요청한다.
 *
 * <p><b>임시 스텁 상태(2026-08-07)</b>: negotiation 도메인에 아직 인바운드 application 계층이 없어
 * {@code infrastructure.negotiation.StubNegotiationAdapter}가 대신 구현한다.
 * negotiation팀이 실제 UseCase를 만들면 이 어댑터가 그 UseCase를 그대로 위임 호출하도록 교체한다.
 */
public interface NegotiationPort {

    /**
     * 매칭 요청 수락 시 협상방을 생성한다. 같은 트랜잭션에서 호출하며, 실패 시 예외를 던져
     * 수락 처리 자체가 롤백되게 한다.
     */
    Long createNegotiation(CreateNegotiationCommand command);

    /** 매칭 요청 상세 화면에 표시할 협상 진행 정보. 협상이 아직 없거나 조회할 수 없으면 empty. */
    Optional<NegotiationSummary> findSummaryByRequestId(Long requestId);
}
