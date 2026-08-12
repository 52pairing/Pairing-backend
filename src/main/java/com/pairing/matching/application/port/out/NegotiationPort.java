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

    /**
     * 타결된 협상의 합의 <b>월 단가</b>(원). 가드 G3(예산 조합)가 "이미 자리를 차지한 사람이 실제로
     * 얼마를 쓰는지" 알아야 해서 쓴다.
     *
     * <p><b>개월 수를 곱하지 말 것.</b> 이 값은 이미 월 단가라 {@code budgetCap}(역시 월 단가 상한)과
     * 단위가 같다. 곱하면 상한이 개월 수배로 부풀어 경고가 영영 안 뜬다(5번 확인, 2026-08-12).
     *
     * <p>아직 타결 전이거나 협상이 없으면 empty. 그때는 호출부가 <b>희망 단가</b>로 대체하는데,
     * 근사가 아니라 정확한 값이다 — 타결가라는 게 아직 존재하지 않거나, 애초에 예산 안에 들어와
     * 협상할 금액 자체가 없었던 경우다.
     */
    Optional<Long> findAgreedMonthlyPay(Long requestId);
}
