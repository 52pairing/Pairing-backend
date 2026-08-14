package com.pairing.matching.application.service;

import com.pairing.matching.domain.model.MatchingRound;
import com.pairing.matching.domain.model.MatchingRoundStatus;
import com.pairing.matching.domain.repository.MatchingRoundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차의 후보 채우기와 실패 처리. 둘을 <b>각각 독립 트랜잭션</b>으로 커밋한다.
 *
 * <p>재추천과 최초 추천이 같이 쓴다(2026-08-13부터). 예전엔 최초 추천만 회차 생성과 AI 호출을 한
 * 트랜잭션에 묶고 있었는데, 그러면 AI 호출이 실패했을 때 회차 행까지 롤백돼 <b>사라진다</b> —
 * 화면은 "추천 준비중"에서 영원히 멈추고 무엇이 실패했는지 아무 데도 안 남는다.
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
class MatchingRoundFiller {

    private final MatchingRoundRepository matchingRoundRepository;
    private final MatchingRoundCreationService matchingRoundCreationService;

    /**
     * <b>같은 회차를 두 번 채우지 않는다.</b> {@code StaleRoundRecoveryService}가 5분마다 멈춘 회차를
     * 다시 채우는데, 서버 인스턴스가 둘 이상이면 각자의 스케줄러가 같은 회차를 집는다. 그대로 두면
     * Gemini를 두 번 부르고 후보가 중복 저장된다.
     *
     * <p><b>상태 확인만으로는 부족하다.</b> 두 트랜잭션이 나란히 {@code RUNNING}을 읽으면 둘 다
     * 진행한다. 스케줄러 크론이 같아서(둘 다 {@code 0 *&#47;5}) 오히려 동시에 뜨는 게 정상이고,
     * 채우기는 AI 호출이라 수십 초 걸리므로 겹칠 시간이 충분하다. 그래서 행을 <b>잠그고</b> 읽는다 —
     * 늦게 온 쪽은 먼저 온 쪽이 커밋할 때까지 기다렸다가 {@code COMPLETED}를 보고 그냥 돌아간다.
     *
     * <p><b>롤링 배포 중에는 항상 잠깐 인스턴스가 둘</b>이다. 태스크가 1개로 설정돼 있어도 이 상황은
     * 배포할 때마다 생긴다.
     *
     * <p>잠금은 이 회차 행 하나뿐이고, 후보 목록 조회 같은 일반 읽기는 막히지 않는다(PostgreSQL MVCC).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    MatchingRound fill(Long roundId) {
        MatchingRound round = matchingRoundRepository.findByIdForUpdate(roundId).orElseThrow();
        if (round.getStatus() != MatchingRoundStatus.RUNNING) {
            return round;
        }
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
