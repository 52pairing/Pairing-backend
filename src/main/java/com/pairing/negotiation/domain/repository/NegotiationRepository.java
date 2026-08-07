package com.pairing.negotiation.domain.repository;

import com.pairing.negotiation.domain.model.Negotiation;

import java.util.List;
import java.util.Optional;

/** 협상 애그리거트 리포지토리 포트. 조건(자식)까지 함께 저장/조회한다. */
public interface NegotiationRepository {

    Negotiation save(Negotiation negotiation);

    Optional<Negotiation> findById(Long id);

    /** 내가 프리랜서인 협상 목록. 인자는 account.id 가 아니라 freelancer_profile.id 다. */
    List<Negotiation> findByFreelancerId(Long freelancerProfileId);

    /** 특정 프로젝트의 협상 목록(클라 협상 탭). 소유 검증은 서비스 계층에서 수행한다. */
    List<Negotiation> findByProjectId(Long projectId);
}
