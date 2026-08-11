package com.pairing.matching.application.service;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재추천 회차의 후보 채우기와 실패 처리. 둘을 <b>각각 독립 트랜잭션</b>으로 커밋한다.
 *
 * <p>한 트랜잭션에서 처리하면 안 된다. 후보 채우기(AI 호출)가 실패하면 그 트랜잭션은
 * rollback-only로 오염되고, 이어서 회차를 FAILED로 저장해도 같이 롤백돼 사라진다. 그러면 회차가
 * {@code RUNNING}으로 영원히 남는다 — {@code MatchingRequestExpirer}에서 겪었던 것과 같은 문제다.
 *
 * <p>{@code @Transactional(REQUIRES_NEW)}는 스프링 프록시를 거쳐야 적용되므로 리스너와 별도 빈으로
 * 뒀다(자기 자신 호출로는 적용되지 않는다).
 */
@Component
@RequiredArgsConstructor
class RerecommendRoundFiller {

    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingRoundCreationService matchingRoundCreationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    MatchingRound fill(Long roundId) {
        MatchingRound round = matchingRoundRepository.findById(roundId).orElseThrow();
        return matchingRoundCreationService.fillCandidates(round);
    }

    /** 실패한 회차를 닫는다. FAILED 회차는 재추천 한도 계산에서 빠져 무료 1회가 되살아난다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    MatchingRound markFailed(Long roundId) {
        MatchingRound round = matchingRoundRepository.findById(roundId).orElseThrow();
        round.fail();
        return matchingRoundRepository.save(round);
    }
}
