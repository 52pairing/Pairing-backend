package com.pairing.matching.domain.repository;

import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.model.MatchingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchingRequestRepository {

    MatchingRequest save(MatchingRequest request);

    Optional<MatchingRequest> findById(Long id);

    /** 같은 포지션에 같은 프리랜서로 중복 요청이 있는지 확인한다(uk_matching_request 근거). */
    Optional<MatchingRequest> findByPositionIdAndFreelancerId(Long positionId, Long freelancerId);

    /** 포지션에 지금 자리를 차지하고 있는(거절/결렬/중도종료가 아닌) 요청 수. 모집 인원 초과 검증(R04)에 쓴다. */
    long countByPositionIdAndStatusNotIn(Long positionId, List<MatchingStatus> excludedStatuses);

    /**
     * 위 count와 같은 기준의 <b>목록</b> 버전. 가드 G3(예산 조합)가 "이미 자리를 차지한 사람들이
     * 얼마를 쓰고 있는지"를 알아야 해서 개수만으로는 부족하다.
     *
     * <p>각 요청의 상태로 단가 출처가 갈린다 — 타결 이후면 협상 타결가, 그 전이면 희망 단가다.
     */
    List<MatchingRequest> findByPositionIdAndStatusNotIn(Long positionId, List<MatchingStatus> excludedStatuses);

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
     * 프로젝트의 요청 중 REJECTED가 아닌(=진행 중이거나 성사/결렬된) 것이 하나라도 있는지.
     * false이면서 위 existsByProjectId가 true면 "전원 거절/만료" 상태로 간주해 무료 재추천 조건을 만족한다(P41/P45).
     * NEGOTIATION_FAILED는 프리랜서의 거절/미응답이 아니므로 무료 재추천 조건에서는 활성 요청으로 본다.
     */
    boolean existsActiveByProjectId(Long projectId);

    /** 프로젝트의 매칭 요청 중 주어진 상태들 중 하나라도 있는지. 프로젝트 대표 단계 재계산(syncStage)에 쓴다. */
    boolean existsByProjectIdAndStatusIn(Long projectId, List<MatchingStatus> statuses);

    /** 응답 기한(3일)이 지났는데 아직 응답 대기(REQUEST_PENDING)인 요청. 자동 만료 스케줄러가 쓴다(정책 P45). */
    List<MatchingRequest> findExpiredPending(LocalDateTime now);

    /**
     * 프로젝트 안에서 특정 상태인 요청들. 계약·정산 이벤트로 단계를 옮길 때 쓴다.
     *
     * <p>상태로 좁혀 조회하는 게 중요하다. 프로젝트에는 거절·만료된 요청도 같이 있는데,
     * 그것까지 {@code advanceStatus}에 넣으면 종결 상태라 예외가 나고, 이벤트가 발행 도메인의
     * 트랜잭션 안에서 처리되므로 계약 체결·결제까지 통째로 롤백된다.
     */
    List<MatchingRequest> findByProjectIdAndStatus(Long projectId, MatchingStatus status);
}
