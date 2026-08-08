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

    /** 체인 머리(가장 최근 로그)의 contentHash. 새 로그 seal 시 직전 해시로 쓴다. 없으면 empty. */
    Optional<String> findLatestHash(Long negotiationId);

    /** 특정 라운드의 AI 제안(PROPOSAL) 개수. 진행조회의 "새 제안 개수"(응답 대기 제안)에 쓴다. */
    int countProposalsInRound(Long negotiationId, int roundNo);
}
