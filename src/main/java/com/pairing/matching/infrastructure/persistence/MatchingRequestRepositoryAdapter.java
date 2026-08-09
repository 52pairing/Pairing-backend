package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.infrastructure.mapper.MatchingRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingRequestRepositoryAdapter implements MatchingRequestRepository {

    private static final List<MatchingStatus> NON_ACTIVE_STATUSES =
            List.of(MatchingStatus.REJECTED, MatchingStatus.NEGOTIATION_FAILED);

    private final SpringDataMatchingRequestRepository springDataRepository;
    private final MatchingRequestMapper matchingRequestMapper;

    @Override
    public MatchingRequest save(MatchingRequest request) {
        MatchingRequestJpaEntity saved = springDataRepository.save(matchingRequestMapper.toJpaEntity(request));
        return matchingRequestMapper.toDomain(saved);
    }

    @Override
    public Optional<MatchingRequest> findById(Long id) {
        return springDataRepository.findById(id).map(matchingRequestMapper::toDomain);
    }

    @Override
    public Optional<MatchingRequest> findByPositionIdAndFreelancerId(Long positionId, Long freelancerId) {
        return springDataRepository.findByPositionIdAndFreelancerId(positionId, freelancerId)
                .map(matchingRequestMapper::toDomain);
    }

    @Override
    public long countByPositionIdAndStatusNotIn(Long positionId, List<MatchingStatus> excludedStatuses) {
        return springDataRepository.countByPositionIdAndStatusNotIn(positionId, excludedStatuses);
    }

    @Override
    public boolean existsByCandidateId(Long candidateId) {
        return springDataRepository.existsByCandidateId(candidateId);
    }

    @Override
    public Page<MatchingRequest> findSentRequests(List<Long> projectIds, Long positionId, MatchingStatus status,
                                                  Pageable pageable) {
        if (projectIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return springDataRepository.findSentRequests(projectIds, positionId, status, pageable)
                .map(matchingRequestMapper::toDomain);
    }

    @Override
    public Page<MatchingRequest> findReceivedRequests(Long freelancerId, List<MatchingStatus> statuses,
                                                       Pageable pageable) {
        boolean hasStatuses = !statuses.isEmpty();
        return springDataRepository.findReceivedRequests(freelancerId, hasStatuses, statuses, pageable)
                .map(matchingRequestMapper::toDomain);
    }

    @Override
    public boolean existsByProjectId(Long projectId) {
        return springDataRepository.existsByProjectId(projectId);
    }

    @Override
    public boolean existsActiveByProjectId(Long projectId) {
        return springDataRepository.existsByProjectIdAndStatusNotIn(projectId, NON_ACTIVE_STATUSES);
    }

    @Override
    public boolean existsByProjectIdAndStatusIn(Long projectId, List<MatchingStatus> statuses) {
        return springDataRepository.existsByProjectIdAndStatusIn(projectId, statuses);
    }
}
