package com.pairing.matching.application.usecase;

import com.pairing.matching.presentation.api.response.MatchingRequestResponse;

import java.util.List;

/** 매칭 요청 발송/수락/거절 인바운드 포트. */
public interface MatchingRequestCommandUseCase {

    /** 선택한 후보들에게 매칭 요청을 보낸다. 모집 인원을 초과해 선택할 수 없다(R04). */
    List<MatchingRequestResponse> sendRequests(Long positionId, List<Long> candidateIds, Long accountId);

    /** 프리랜서가 요청을 수락한다. 협상방 생성까지 같은 트랜잭션에서 처리하며, 실패 시 수락도 롤백된다. */
    MatchingRequestResponse accept(Long requestId, Long accountId);

    /** 프리랜서가 요청을 거절한다. 사유는 알림 문구에만 쓰고 영속화하지 않는다. */
    MatchingRequestResponse reject(Long requestId, String reason, Long accountId);

    /**
     * 응답 기한(3일)이 지난 요청을 자동으로 거절(만료) 처리한다(정책 P45). 스케줄러 전용.
     *
     * @return 처리한 건수
     */
    int expireOverdueRequests();
}
