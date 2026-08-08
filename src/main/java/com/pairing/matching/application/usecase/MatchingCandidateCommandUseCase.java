package com.pairing.matching.application.usecase;

import com.pairing.matching.presentation.api.response.CandidateListResponse;

/** 후보 거절(비활성 표시) 인바운드 포트. */
public interface MatchingCandidateCommandUseCase {

    /** 클라이언트가 후보를 거절한다. 요청을 보낸 적 없어도 가능하며, 이후 회차에도 다시 노출되지 않는다. */
    CandidateListResponse rejectCandidate(Long candidateId, Long accountId);
}
