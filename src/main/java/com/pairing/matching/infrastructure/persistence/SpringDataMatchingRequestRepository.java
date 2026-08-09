package com.pairing.matching.infrastructure.persistence;

import com.pairing.matching.domain.model.MatchingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataMatchingRequestRepository extends JpaRepository<MatchingRequestJpaEntity, Long> {

    Optional<MatchingRequestJpaEntity> findByPositionIdAndFreelancerId(Long positionId, Long freelancerId);

    long countByPositionIdAndStatusNotIn(Long positionId, List<MatchingStatus> excludedStatuses);

    boolean existsByCandidateId(Long candidateId);

    boolean existsByProjectId(Long projectId);

    boolean existsByProjectIdAndStatusNotIn(Long projectId, List<MatchingStatus> excludedStatuses);

    boolean existsByProjectIdAndStatusIn(Long projectId, List<MatchingStatus> statuses);

    @Query("select m from MatchingRequestJpaEntity m where m.projectId in :projectIds "
            + "and (:positionId is null or m.positionId = :positionId) "
            + "and (:status is null or m.status = :status)")
    Page<MatchingRequestJpaEntity> findSentRequests(@Param("projectIds") List<Long> projectIds,
                                                    @Param("positionId") Long positionId,
                                                    @Param("status") MatchingStatus status,
                                                    Pageable pageable);

    @Query("select m from MatchingRequestJpaEntity m where m.freelancerId = :freelancerId "
            + "and (:hasStatuses = false or m.status in :statuses)")
    Page<MatchingRequestJpaEntity> findReceivedRequests(@Param("freelancerId") Long freelancerId,
                                                        @Param("hasStatuses") boolean hasStatuses,
                                                        @Param("statuses") List<MatchingStatus> statuses,
                                                        Pageable pageable);
}
