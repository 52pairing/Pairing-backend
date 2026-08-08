package com.pairing.negotiation.application.usecase;

import java.util.Optional;

/**
 * 협상 진행 정보 조회 인바운드 포트(매칭 도메인 연동용). 매칭 요청 카드에서 협상 상태를 표시할 때
 * 매칭이 <b>requestId 기준</b>으로 동기 조회한다.
 *
 * <p>협상 생성 포트({@code NegotiationCommandUseCase})와 대칭인 "매칭↔협상" 통합 지점이다.
 * 화면용 조회({@code NegotiationQueryUseCase})와 달리 뷰어(account) 개념이 없다.
 */
public interface NegotiationProgressUseCase {

    /**
     * 매칭 요청에 연결된 협상의 진행 정보. 협상이 아직 없으면 {@link Optional#empty()}.
     *
     * @param requestId 매칭 요청 ID (negotiation.request_id, UNIQUE)
     */
    Optional<NegotiationProgress> findProgressByRequestId(Long requestId);

    /**
     * 매칭 카드용 진행 요약.
     *
     * @param negotiationId    협상 ID
     * @param currentRound     현재 진행 라운드(0 = 아직 마지노선 미설정/미시작)
     * @param maxRound         라운드 상한(15). 소진 시 자동 결렬
     * @param newProposalCount 현재 라운드의 새 AI 제안 개수(응답 대기 중인 제안 수)
     */
    record NegotiationProgress(
            Long negotiationId,
            int currentRound,
            int maxRound,
            int newProposalCount
    ) {
    }
}
