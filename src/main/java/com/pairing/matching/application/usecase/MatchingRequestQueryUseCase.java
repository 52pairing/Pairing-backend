package com.pairing.matching.application.usecase;

import com.pairing.global.common.api.response.PageResponse;
import com.pairing.matching.domain.model.MatchingRequestTab;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.presentation.api.response.MatchingRequestResponse;

/** 매칭 요청 조회 인바운드 포트. */
public interface MatchingRequestQueryUseCase {

    /** 클라이언트가 보낸 요청 목록. projectId가 없으면 계정이 소유한 프로젝트 전체 기준으로 조회한다. */
    PageResponse<MatchingRequestResponse> findSentRequests(Long projectId, Long positionId, MatchingStatus status,
                                                           int page, int size, Long accountId);

    /** 프리랜서가 받은 요청 목록(탭별 상태 필터). */
    PageResponse<MatchingRequestResponse> findReceivedRequests(MatchingRequestTab tab, int page, int size,
                                                               Long accountId);

    /** 요청 상세. 당사자(요청을 보낸 클라이언트 또는 받은 프리랜서)만 열람 가능하다. */
    MatchingRequestResponse findRequest(Long requestId, Long accountId);
}
