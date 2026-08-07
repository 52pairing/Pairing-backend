package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingCandidate;
import com.pairing.matching.domain.repository.MatchingCandidateRepository;
import com.pairing.matching.infrastructure.mapper.MatchingCandidateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingCandidateRepositoryAdapter implements MatchingCandidateRepository {

    private final SpringDataMatchingCandidateRepository springDataRepository;
    private final MatchingCandidateMapper matchingCandidateMapper;

    @Override
    public MatchingCandidate save(MatchingCandidate candidate) {
        MatchingCandidateJpaEntity saved = springDataRepository.save(matchingCandidateMapper.toJpaEntity(candidate));
        return matchingCandidateMapper.toDomain(saved);
    }

    @Override
    public List<MatchingCandidate> saveAll(List<MatchingCandidate> candidates) {
        List<MatchingCandidateJpaEntity> entities = candidates.stream()
                .map(matchingCandidateMapper::toJpaEntity)
                .toList();

        return springDataRepository.saveAll(entities).stream()
                .map(matchingCandidateMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<MatchingCandidate> findById(Long id) {
        return springDataRepository.findById(id).map(matchingCandidateMapper::toDomain);
    }

    @Override
    public List<MatchingCandidate> findByRoundIdOrderByRankNo(Long roundId) {
        return springDataRepository.findByRoundIdOrderByRankNoAsc(roundId).stream()
                .map(matchingCandidateMapper::toDomain)
                .toList();
    }

    @Override
    public List<MatchingCandidate> findByRoundIdAndExposedTrueOrderByRankNo(Long roundId) {
        return springDataRepository.findByRoundIdAndExposedTrueOrderByRankNoAsc(roundId).stream()
                .map(matchingCandidateMapper::toDomain)
                .toList();
    }

    @Override
    public List<Long> findFreelancerIdsByProjectId(Long projectId) {
        return springDataRepository.findDistinctFreelancerIdByProjectId(projectId);
    }
}
