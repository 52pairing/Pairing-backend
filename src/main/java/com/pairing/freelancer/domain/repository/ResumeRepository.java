package com.pairing.freelancer.domain.repository;

import com.pairing.freelancer.domain.model.Resume;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResumeRepository {

    Resume save(Resume resume);

    Optional<Resume> findByAccountId(Long accountId);

    /**
     * 여러 계정의 이력서를 한 번에. 매칭 도메인이 노출 확정 시점에 후보마다
     * {@link #findByAccountId} 를 부르면 N+1 이라 만들었다.
     *
     * <p>이력서를 등록하지 않은 계정은 결과에 없다.
     */
    List<Resume> findByAccountIdIn(Collection<Long> accountIds);

    /** 이력서를 등록한 모든 계정 ID. 임베딩 일괄 재색인 대상을 고르는 데 쓴다. */
    List<Long> findAllAccountIds();
}
