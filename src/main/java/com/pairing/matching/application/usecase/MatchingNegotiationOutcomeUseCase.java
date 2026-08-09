package com.pairing.matching.application.usecase;

/**
 * 협상(negotiation) 도메인이 타결/결렬 결과를 매칭 요청 건에 반영할 때 쓰는 인바운드 포트.
 *
 * <p>negotiation 쪽의 {@code NegotiationLoopService}가 {@code Negotiation.agree()}/{@code .fail()}을
 * 호출하는 시점에 같이 호출한다(같은 요청 건을 가리키는 {@code requestId}로 연결). 매칭이 negotiation을
 * 호출하는 방향({@link com.pairing.matching.application.port.out.NegotiationPort})과 반대로,
 * negotiation이 매칭을 호출하는 방향의 포트다.
 */
public interface MatchingNegotiationOutcomeUseCase {

    /** 협상이 타결됐다. 매칭 요청 상태를 계약 대기(CONTRACT_PENDING)로 전환한다. */
    void markNegotiationAgreed(Long requestId);

    /** 협상이 결렬됐다. 매칭 요청 상태를 협상 결렬(NEGOTIATION_FAILED)로 종결한다. */
    void markNegotiationFailed(Long requestId);
}
