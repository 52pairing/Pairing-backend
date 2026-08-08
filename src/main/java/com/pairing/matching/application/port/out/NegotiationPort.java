package com.pairing.matching.application.port.out;

import com.pairing.matching.application.command.CreateNegotiationCommand;
import com.pairing.matching.application.result.NegotiationSummary;

import java.util.Optional;

/**
 * negotiation 도메인 호출 포트. 매칭은 이 인터페이스로만 협상 생성·조회를 요청한다.
 *
 * <p>{@code infrastructure.negotiation.NegotiationAdapter}가 negotiation 도메인의
 * {@code NegotiationCommandUseCase}/{@code NegotiationProgressUseCase}를 위임 호출해 구현한다.
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
