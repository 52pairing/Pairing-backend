package com.pairing.negotiation.domain.repository;

import com.pairing.negotiation.domain.model.Negotiation;

import java.util.List;
import java.util.Optional;

/** 협상 애그리거트 리포지토리 포트. 조건(자식)까지 함께 저장/조회한다. */
public interface NegotiationRepository {

    Negotiation save(Negotiation negotiation);

    Optional<Negotiation> findById(Long id);

    /** 내 협상 목록(클라이언트/프리랜서 공통). projectId 가 있으면 해당 프로젝트로 필터(클라 협상 탭). */
    List<Negotiation> findMine(Long accountId, Long projectId);
}
