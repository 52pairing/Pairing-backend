package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface MatchingRequestRepository {

    MatchingRequest save(MatchingRequest request);

    Optional<MatchingRequest> findById(Long id);

    /** 같은 포지션에 같은 프리랜서로 중복 요청이 있는지 확인한다(uk_matching_request 근거). */
    Optional<MatchingRequest> findByPositionIdAndFreelancerId(Long positionId, Long freelancerId);

    /** 포지션에 지금 자리를 차지하고 있는(거절/결렬/중도종료가 아닌) 요청 수. 모집 인원 초과 검증(R04)에 쓴다. */
    long countByPositionIdAndStatusNotIn(Long positionId, List<MatchingStatus> excludedStatuses);

    /** 이 후보에게 이미 매칭 요청을 보냈는지(CandidateResponse.requested). */
    boolean existsByCandidateId(Long candidateId);

    /**
     * 클라이언트가 보낸 요청 목록. projectIds가 비어있으면 빈 페이지를 돌려준다
     * (호출 측이 계정 소유 프로젝트 목록을 먼저 구해서 넘긴다 — projectId 파라미터 없이 조회하는 경우).
     */
    Page<MatchingRequest> findSentRequests(List<Long> projectIds, Long positionId, MatchingStatus status,
                                           Pageable pageable);

    /** 프리랜서가 받은 요청 목록(탭별 상태 필터). statuses가 비어있으면 전체 상태. */
    Page<MatchingRequest> findReceivedRequests(Long freelancerId, List<MatchingStatus> statuses, Pageable pageable);

    /** 프로젝트에 지금까지 보낸 요청이 하나라도 있는지(무료 재추천 조건 판단의 전제, P41). */
    boolean existsByProjectId(Long projectId);

    /**
     * 프로젝트의 요청 중 REJECTED/NEGOTIATION_FAILED가 아닌(=진행 중이거나 성사된) 것이 하나라도 있는지.
     * false이면서 위 existsByProjectId가 true면 "전원 거절" 상태로 간주해 무료 재추천 조건을 만족한다(P41).
     * NEGOTIATION_FAILED는 협상 결렬로 별도 사유이며 이 조건 판단에서는 제외한다(정책 문서 명시).
     */
    boolean existsActiveByProjectId(Long projectId);

    /** 프로젝트의 매칭 요청 중 주어진 상태들 중 하나라도 있는지. 프로젝트 대표 단계 재계산(syncStage)에 쓴다. */
    boolean existsByProjectIdAndStatusIn(Long projectId, List<MatchingStatus> statuses);
}
