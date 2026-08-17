package com.pairing.freelancer.infrastructure.persistence;

import com.pairing.freelancer.domain.model.Resume;
import com.pairing.freelancer.domain.repository.ResumeRepository;
import com.pairing.freelancer.infrastructure.mapper.ResumeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ResumeRepositoryAdapter implements ResumeRepository {

    private final SpringDataResumeRepository springDataRepository;
    private final ResumeMapper resumeMapper;

    @Override
    public Resume save(Resume resume) {
        ResumeJpaEntity saved = springDataRepository.save(resumeMapper.toJpaEntity(resume));
        return resumeMapper.toDomain(saved);
    }

    @Override
    public Optional<Resume> findByAccountId(Long accountId) {
        return springDataRepository.findByAccountId(accountId).map(resumeMapper::toDomain);
    }

    @Override
    public List<Resume> findByAccountIdIn(Collection<Long> accountIds) {
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return springDataRepository.findByAccountIdIn(accountIds).stream()
                .map(resumeMapper::toDomain)
                .toList();
    }

    @Override
    public List<Long> findAllAccountIds() {
        return springDataRepository.findAllAccountIds();
    }
}
