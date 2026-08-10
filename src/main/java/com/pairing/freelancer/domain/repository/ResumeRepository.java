package com.pairing.freelancer.domain.repository;

import com.pairing.freelancer.domain.model.Resume;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository {

    Resume save(Resume resume);

    Optional<Resume> findByAccountId(Long accountId);

    /** 이력서를 등록한 모든 계정 ID. 임베딩 일괄 재색인 대상을 고르는 데 쓴다. */
    List<Long> findAllAccountIds();
}
