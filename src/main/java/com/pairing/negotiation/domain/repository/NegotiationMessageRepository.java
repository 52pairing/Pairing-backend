package com.pairing.negotiation.domain.repository;

import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.SenderType;

import java.time.LocalDateTime;
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

    /** 협상의 최신 AI 제안(조건 무관). 목록 카드의 lastProposalBy/lastProposalAt 표시에 쓴다. */
    Optional<NegotiationMessage> findLatestProposal(Long negotiationId);

    /** 특정 라운드에 이 주체(sender)가 남긴 응답(RESPONSE) 개수. "내 응답 필요(waitingForMe)" 판정용. */
    int countResponsesInRound(Long negotiationId, SenderType senderType, int roundNo);

    /**
     * {@code after} 이후 생성된 AI 제안(PROPOSAL) 수. {@code after} 가 null 이면(아직 안 읽음) 전체 제안 수.
     * 매칭 카드의 "확인하지 않은 새 제안 수"(마지막 읽음 이후 온 제안) 계산에 쓴다.
     */
    int countUnreadProposals(Long negotiationId, LocalDateTime after);

    /** 체인 머리(가장 최근 로그)의 contentHash. 새 로그 seal 시 직전 해시로 쓴다. 없으면 empty. */
    Optional<String> findLatestHash(Long negotiationId);

    /** 특정 라운드의 AI 제안(PROPOSAL) 개수. 진행조회의 "새 제안 개수"(응답 대기 제안)에 쓴다. */
    int countProposalsInRound(Long negotiationId, int roundNo);
}
