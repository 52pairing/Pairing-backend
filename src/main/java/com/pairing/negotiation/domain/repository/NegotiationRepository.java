package com.pairing.negotiation.domain.repository;

import com.pairing.negotiation.domain.model.Negotiation;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/** 협상 애그리거트 리포지토리 포트. 상세는 조건(자식)까지, 목록은 DB 페이징으로 조회한다. */
public interface NegotiationRepository {

    Negotiation save(Negotiation negotiation);

    Optional<Negotiation> findById(Long id);

    /**
     * 매칭 요청(request_id, UNIQUE) 기준 협상 요약 조회. 매칭 도메인의 진행조회 연동용.
     * 조건(자식)은 로드하지 않는다(요약). 협상이 아직 없으면 empty.
     */
    Optional<Negotiation> findByRequestId(Long requestId);

    /**
     * 내가 프리랜서인 협상 목록(DB 페이징). 인자는 account.id 가 아니라 freelancer_profile.id 다.
     * 목록은 조건(자식)을 로드하지 않는 요약이다. status 가 있으면 해당 상태로 필터한다.
     */
    Page<Negotiation> findByFreelancerId(Long freelancerProfileId, NegotiationStatus status, Pageable pageable);

    /** 특정 프로젝트의 협상 목록(클라 협상 탭, DB 페이징). 소유 검증은 서비스 계층에서 수행한다. */
    Page<Negotiation> findByProjectId(Long projectId, NegotiationStatus status, Pageable pageable);
}
