package com.pairing.matching.application.usecase;

import com.pairing.matching.presentation.api.response.CandidateListResponse;

/** 후보 조회 인바운드 포트. */
public interface MatchingCandidateQueryUseCase {

    /** 포지션의 가장 최근 회차에서 노출 대상 후보를 조회한다. */
    CandidateListResponse findCandidates(Long positionId, Long accountId);
}
