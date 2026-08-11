package com.pairing.freelancer.domain.repository;

import com.pairing.freelancer.domain.model.ResumeDraft;

import java.util.Optional;

public interface ResumeDraftRepository {

    ResumeDraft save(ResumeDraft draft);

    Optional<ResumeDraft> findByAccountId(Long accountId);

    /** 이력서를 정식 등록하면 초안은 쓸모가 없어진다. 없어도 조용히 넘어간다. */
    void deleteByAccountId(Long accountId);
}
