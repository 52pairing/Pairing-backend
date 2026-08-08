package com.pairing.freelancer.domain.repository;

import com.pairing.freelancer.domain.model.Resume;

import java.util.Optional;

public interface ResumeRepository {

    Resume save(Resume resume);

    Optional<Resume> findByAccountId(Long accountId);
}
