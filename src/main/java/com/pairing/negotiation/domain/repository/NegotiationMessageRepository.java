package com.pairing.negotiation.domain.repository;

import com.pairing.negotiation.domain.model.NegotiationMessage;

import java.util.List;
import java.util.Optional;

/** 협상 메시지(로그) 리포지토리 포트. */
public interface NegotiationMessageRepository {

    NegotiationMessage save(NegotiationMessage message);

    List<NegotiationMessage> saveAll(List<NegotiationMessage> messages);

    /** 협상의 전체 로그(라운드 오름차순, 같은 라운드는 생성 순). */
    List<NegotiationMessage> findByNegotiationId(Long negotiationId);

    /** 특정 조건의 최신 AI 제안(수락 시 락할 값 조회용). */
    Optional<NegotiationMessage> findLatestProposal(Long negotiationId, Long conditionId);
}
