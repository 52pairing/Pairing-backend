package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.matching.infrastructure.mapper.MatchingRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MatchingRequestRepositoryAdapter implements MatchingRequestRepository {

    /**
     * 무료 재추천 판정(P41)에서 "무료 재추천을 열어줘도 되는" 상태. {@code REJECTED} 하나뿐이다.
     *
     * <p><b>{@code MatchingRequest.isTerminal()}과 일부러 다르다.</b> 도메인의 isTerminal은
     * NEGOTIATION_FAILED/TERMINATED/CLOSED도 종결로 보지만, 무료 재추천 판정에는 넣으면 안 된다 —
     * P41이 "모든 프리랜서가 <b>전부 거절했거나 응답 기한 초과로 자동 만료된 경우에만</b>"으로
     * 한정하고, 이어서 "<b>협상 결렬, 계약 전 파기, 계약 후 중도 종료는 무료 재추천 조건에
     * 포함하지 않는다</b>"고 못 박고 있어서다. 무료 재추천은 "요청한 사람이 전원 거절해 아무도 못
     * 구한" 경우의 보상이지, 협상까지 갔다가 틀어진 경우는 유료로 다시 찾아야 한다.
     *
     * <p>만료도 {@code REJECTED}로 저장되므로(P45) 이 하나로 두 경우가 다 커버된다.
     */
    private static final List<MatchingStatus> NON_ACTIVE_STATUSES = List.of(MatchingStatus.REJECTED);

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

    @Override
    public List<MatchingRequest> findExpiredPending(LocalDateTime now) {
        return springDataRepository.findByStatusAndExpiresAtBefore(MatchingStatus.REQUEST_PENDING, now).stream()
                .map(matchingRequestMapper::toDomain)
                .toList();
    }

    @Override
    public List<MatchingRequest> findByProjectIdAndStatus(Long projectId, MatchingStatus status) {
        return springDataRepository.findByProjectIdAndStatus(projectId, status).stream()
                .map(matchingRequestMapper::toDomain)
                .toList();
    }
}
