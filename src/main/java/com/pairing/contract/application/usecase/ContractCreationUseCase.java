package com.pairing.contract.application.usecase;

/**
 * 협상 도메인이 쓰는 인바운드 포트.
 *
 * <p>협상이 타결되면 표준계약서가 자동 생성된다(요구사항 44행). 협상 타결과 같은 트랜잭션에서
 * 부르면 계약 생성이 실패했을 때 타결도 함께 롤백된다. 협상방 생성·매칭 상태 전환과 같은 방식이다.
 *
 * <p>구현은 협상 리포지토리를 직접 읽지 않고 {@code NegotiationQueryUseCase.getAgreedForContract}
 * 로 합의값을 당겨온다.
 */
public interface ContractCreationUseCase {

    /**
     * 타결된 협상으로 계약서를 만든다. 상태는 서명 대기(SIGN_PENDING)로 시작한다.
     *
     * <p>협상 1건당 계약 1건이다. 이미 만들어져 있으면 새로 만들지 않고 기존 ID 를 돌려준다.
     * 타결되지 않은 협상이면 협상 쪽에서 NG_009 가 난다.
     *
     * @return 계약 ID
     */
    Long createFromNegotiation(Long negotiationId);
}
